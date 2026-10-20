# Account Ledger

[![CI](https://github.com/Mojolaoluwajoy/account-ledger/actions/workflows/ci.yml/badge.svg)](https://github.com/Mojolaoluwajoy/account-ledger/actions/workflows/ci.yml)

Deposit accounts and a double-entry ledger, built as Spring Boot microservices:
clients, savings accounts, transfers and fixed deposits, with every movement of
money recorded in the ledger, behind one API gateway.
Clients are also created in [Apache Fineract](https://fineract.apache.org/)
(the Mifos sandbox by default).

## Services

| Service | What it does | Port on your machine |
|---|---|---|
| `api-gateway` | Single entry point; routes `/api/v1/...` to the services below and serves one Swagger UI for all of them | **8090** |
| `client-service` | Clients, kept in sync with Fineract | 8086 |
| `savings-service` | Savings products and accounts: deposits, withdrawals, transfers | 8082 |
| `fixed-deposit-service` | Fixed deposits, paid into a savings account at maturity | 8083 |
| `ledger-service` | Double-entry ledger (chart of accounts and entries) | 8084 |
| `postgres` | One database per service | 5432 |

Use the gateway (port 8090) for everything; the other ports are there for debugging.

## Running it

You need Java 21, Maven and Docker.

```bash
mvn clean package -DskipTests      # build the jars the Docker images copy in
docker compose up --build -d       # start Postgres and all five services
```

Postgres creates the four databases on first start (`init-db.sql`), and each
service creates its own tables with Flyway when it starts.

- Swagger UI: http://localhost:8090/swagger-ui.html (pick a service from the dropdown at the top right)
- Stop everything: `docker compose down` (add `-v` to also delete the data)

## Trying it out

The order matters: a client must be **ACTIVE** before it can open a savings
account or a fixed deposit, and a fixed deposit needs a savings account to pay
into when it matures.

```bash
G=http://localhost:8090/api/v1
J='Content-Type: application/json'

# 1. A savings product
curl -X POST $G/savings-products -H "$J" -d '{"name":"Basic Savings","shortName":"BSAV",
  "nominalAnnualInterestRate":3.5,"minimumOpeningBalance":100,"minimumBalance":50,
  "enforceMinimumBalance":true}'

# 2. A client. It starts PENDING and becomes ACTIVE once Fineract accepts it,
#    usually straight away. Check with: curl $G/clients/1
curl -X POST $G/clients -H "$J" -d '{"firstName":"Ada","lastName":"Obi",
  "email":"ada@example.com","phoneNumber":"08030000001","dateOfBirth":"1990-04-02"}'

# 3. A savings account, then money in and out
curl -X POST $G/savings-accounts -H "$J" -d '{"clientId":1,"productId":1,"initialDeposit":500}'
curl -X POST $G/savings-accounts/1/deposit  -H "$J" -d '{"amount":250}'
curl -X POST $G/savings-accounts/1/withdraw -H "$J" -d '{"amount":100}'
curl "$G/savings-accounts/1/transactions?page=0&size=20"

# 4. A transfer to another savings account (open a second one first)
curl -X POST $G/savings-accounts/1/transfer -H "$J" -d '{"destinationAccountId":2,"amount":50}'

# 5. A fixed deposit that pays into savings account 1 when it matures
curl -X POST $G/fixed-deposits -H "$J" -d '{"clientId":1,"principalAmount":10000,
  "interestRate":10,"tenureInDays":90,"payoutAccountId":1}'
curl -X POST $G/fixed-deposits/1/mature     # only allowed on or after the maturity date

# 6. What reached the ledger
curl "$G/ledger/entries?page=0&size=20"
```

## How things fit together

- **Ledger postings.** Savings and fixed-deposit changes are recorded together
  with a ledger posting in the same database transaction, and a background job
  sends the posting to the ledger service every few seconds. If the ledger
  service is down, postings wait and are retried; the ledger ignores a
  reference it has already posted, so nothing is posted twice.
- **Fixed deposit payouts.** Maturing a deposit marks its payout `PENDING`. A
  background job credits principal and interest to the payout account through
  savings-service and marks it `PAID`. A payout is credited at most once.
- **Failures.** A posting or payout the other service rejects outright (a 4xx
  answer) is marked `FAILED` with the reason, instead of being retried
  forever. See the admin endpoints below to list and retry them.
- **Interest.** Every night (00:15) each active savings account earns
  `end-of-day balance x annual rate / 365` for the day before, kept to 6
  decimal places; nothing on a day it ends below the product's minimum
  balance. On the 1st of the month the previous month's interest is added up,
  rounded half-up to 2 places and paid into the account as one `INTEREST`
  transaction (ledger: debit 5001 Interest Expense, credit 2001). A month that
  rounds to 0.00 is carried into the next. Missed nights are caught up, and
  running twice pays nothing twice.
- **Closing an account.** `POST /savings-accounts/{id}/close` pays the
  interest earned up to yesterday, moves whatever is left to
  `{"transferToAccountId": 2}` (required if anything is left) and marks the
  account `CLOSED`. A closed account takes no more money in or out, and earns
  no interest. A fixed deposit payout due to a closed account fails and can be
  retried into another account (see the admin endpoints).
- **Concurrency.** Balance changes lock the account row; transfers lock both
  rows, lowest ID first. A request gives up with a 503 after waiting 3 seconds
  for a lock. Client updates can send back the `version` they were based on and
  are refused with a 409 if someone changed the client in the meantime.

### Chart of accounts

| Code | Account | Type |
|---|---|---|
| 1001 | Cash | Asset |
| 1002 | Fixed Deposit Asset | Asset |
| 2001 | Savings Control | Liability |
| 2002 | Fixed Deposit Liability | Liability |
| 4001 | Interest Income | Income |
| 5001 | Interest Expense | Expense |

Transfers between savings accounts post nothing: both sides sit under 2001.

## API

All responses look like `{"success": true, "message": "...", "data": ...}`.
Full request and response details are in the Swagger UI.

| Area | Endpoints |
|---|---|
| Clients | `POST /clients`, `GET /clients`, `GET /clients/{id}`, `PUT /clients/{id}` |
| Savings products | `POST /savings-products`, `GET /savings-products`, `GET /savings-products/{id}` |
| Savings accounts | `POST /savings-accounts`, `GET /savings-accounts/{id}`, `GET /savings-accounts/client/{clientId}`, `POST /savings-accounts/{id}/deposit`, `POST .../withdraw`, `POST .../transfer`, `GET .../transactions`, `POST .../close` |
| Fixed deposits | `POST /fixed-deposits`, `GET /fixed-deposits/{id}`, `GET /fixed-deposits/client/{clientId}`, `POST /fixed-deposits/{id}/mature` |
| Ledger | `GET /ledger/entries`, `GET /ledger/entries/{id}`, `GET /ledger/entries/reference/{reference}` |

All paths start with `/api/v1`. Two endpoints are meant for the services
themselves rather than people: `POST /ledger/entries` and
`POST /savings-accounts/{id}/fixed-deposit-payouts`.

**Pages.** `GET /clients`, `GET /ledger/entries` and
`GET /savings-accounts/{id}/transactions` return one page at a time:
`?page=0&size=20` (zero-based page; size 1 to 100, default 20). `data` is then
`{"content": [...], "page": 0, "size": 20, "totalElements": 57, "totalPages": 3}`.

### Admin endpoints

For looking after failed postings and payouts, and running interest:

| Endpoint | What it does |
|---|---|
| `GET /admin/savings/ledger-postings?status=FAILED` | Savings postings by status (`PENDING`, `POSTED`, `FAILED`; default `FAILED`) |
| `POST /admin/savings/ledger-postings/{id}/retry` | Put a `FAILED` savings posting back in the queue |
| `GET /admin/fixed-deposits/ledger-postings?status=FAILED` | The same for fixed deposit postings |
| `POST /admin/fixed-deposits/ledger-postings/{id}/retry` | The same for fixed deposit postings |
| `GET /admin/fixed-deposits/payouts?status=FAILED` | Fixed deposits by payout status |
| `POST /admin/fixed-deposits/{id}/payout/retry` | Put a `FAILED` payout back in the queue; optionally send `{"payoutAccountId": 3}` to pay into another active account of the same client |
| `POST /admin/savings/interest/run?asOf=2026-11-01` | Run the nightly interest job now, as if today were `asOf` (default today; not in the future) |

Fix whatever caused the failure before retrying, or it will fail again.

## Configuration

Set in `docker-compose.yml`; the defaults in each service's
`application.properties` suit running a service directly on your machine.

| Variable | Used by | Default |
|---|---|---|
| `SPRING_DATASOURCE_URL`, `_USERNAME`, `_PASSWORD` | all but the gateway | local Postgres, `root` / `postgres` |
| `FINERACT_BASE_URL`, `FINERACT_TENANT_ID`, `FINERACT_USERNAME`, `FINERACT_PASSWORD` | client-service | Mifos sandbox, `default`, `mifos` / `password` |
| `CLIENT_SERVICE_URL` | savings, fixed-deposit, gateway | `http://localhost:8081` |
| `SAVINGS_SERVICE_URL` | fixed-deposit, gateway | `http://localhost:8082` |
| `LEDGER_SERVICE_URL` | savings, fixed-deposit, gateway | `http://localhost:8084` |
| `FIXED_DEPOSIT_SERVICE_URL` | gateway | `http://localhost:8083` |

If Fineract cannot be reached, new clients stay `PENDING` (with the reason in
`fineractSyncError`) and are retried every minute.

## Tests and formatting

```bash
mvn test        # unit and integration tests; they use an in-memory H2 database, so no Docker needed
mvn verify      # also checks formatting (Google Java Format via Spotless)
mvn spotless:apply   # fix formatting
```

## Branches and CI

- `dev` collects finished work through pull requests; `main` is updated from `dev`.
- GitHub Actions (`.github/workflows/ci.yml`) runs on every push and pull request
  to `main` and `dev`: it builds every service, runs the tests, checks the
  formatting, validates `docker-compose.yml` and builds the Docker images.

## Not done yet

- There is no authentication: anyone who can reach the gateway can call every
  endpoint, including the admin ones.
