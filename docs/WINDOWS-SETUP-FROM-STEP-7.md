# Banking Ledger Lite: Windows Setup Guide from Step 7

This guide continues after WSL 2, Docker Desktop, Python 3, IntelliJ IDEA, and Java 21 have already been installed.

The commands below are for Windows PowerShell and the `banking-ledger-lite` version of the project.

## Step 7: Open the correct project folder

Open the extracted project in IntelliJ IDEA. In IntelliJ, open the **Terminal** tab and confirm that the terminal type is PowerShell.

Your current project path is:

```powershell
cd "D:\Projects\Banking Ledger\banking-ledger-lite\banking-ledger-lite"
```

The quotation marks are required because `Banking Ledger` contains a space.

Confirm that you are in the folder containing the project files:

```powershell
Get-Item pom.xml, compose.yml
Get-Item scripts\lite.py, scripts\init_env.py
```

All four files should be displayed. If PowerShell reports that one of them does not exist, use this command to see your current location:

```powershell
Get-Location
```

## Step 8: Start Docker Desktop

Open Docker Desktop and wait until it reports that the Docker engine is running.

In PowerShell, verify Docker:

```powershell
docker --version
docker compose version
docker info
```

`docker info` must complete successfully and should report Linux containers. If it reports that it cannot connect to the Docker daemon, keep Docker Desktop open and wait for it to finish starting.

Check whether the lightweight launcher can communicate with Docker:

```powershell
py scripts/lite.py check
```

The command displays the amount of memory available to Docker. A warning about limited memory does not automatically mean startup will fail. Close browsers, games, Android emulators, and other memory-heavy applications before the first build.

## Step 9: Generate local credentials

Run:

```powershell
py scripts/init_env.py
```

The correct filename is `init_env.py`, with a dot before `py`.

Expected output:

```text
Created local .env. Do not commit it.
```

Confirm that the hidden file exists:

```powershell
Get-Item .env
```

The `.env` file contains generated development passwords and a JWT signing secret. Do not upload it to GitHub and do not paste its contents into screenshots or messages.

If the script says that `.env` already exists, that is normal. It preserves the existing credentials.

## Step 10: Perform the first build and startup

For the first attempt, close IntelliJ after opening this guide so that more memory remains available. Open a separate PowerShell window and return to the project directory:

```powershell
cd "D:\Projects\Banking Ledger\banking-ledger-lite\banking-ledger-lite"
```

Start the lightweight stack:

```powershell
py scripts/lite.py start
```

You can also double-click `start-lite.bat`, but running the Python command in PowerShell makes errors easier to read.

The launcher performs these operations:

1. Checks Docker and validates `compose.yml`.
2. Preserves existing database volumes.
3. Makes the Kafka volume writable by the official image's non-root user.
4. Builds the Maven project sequentially in a memory-limited container.
5. Packages the six Java service images one at a time.
6. Starts PostgreSQL, Redis, Kafka, and the six Java services one at a time.
7. Waits for each component to become healthy before continuing.

The first build downloads Docker images and Maven dependencies, so it can take several minutes. Keep the PowerShell window open. Do not run a second copy of the start command while the first is working.

A successful startup ends with output similar to:

```text
Ready at http://localhost:8080. This is an API, not a website.
Next: py scripts/lite.py test
Memory: py scripts/lite.py stats
```

Do not use `docker compose up --build` for this lightweight project. The launcher prepares the JAR files in a separate sequential build step.

## Step 11: Check service status

Run:

```powershell
py scripts/lite.py status
```

The base system contains nine running containers:

| Container | Purpose |
| --- | --- |
| `postgres` | Stores service databases and ledger data |
| `redis` | Stores rate-limit counters |
| `kafka` | Carries business events |
| `account` | Manages account profiles |
| `ledger` | Manages balances, reservations, and journal entries |
| `fraud` | Produces the demonstration fraud decision |
| `notification` | Processes events and analytics |
| `payment` | Coordinates the payment saga |
| `gateway` | Exposes the API on port 8080 |

The containers should show a running or healthy state. The Maven builder is temporary and should not remain running after the build.

## Step 12: Run the complete smoke test

Run:

```powershell
py scripts/lite.py test
```

The test may take several seconds because payment processing is asynchronous. It checks:

- A successful transfer from Alice to Bob.
- Duplicate-request idempotency.
- Rejection when an idempotency key is reused with different input.
- User authorization for payments and balances.
- Fraud rejection and release of reserved funds.
- Ledger reconciliation.
- Kafka notification and analytics processing.

Expected final output:

```text
PASS: transfer, duplicate, conflict, authorization, fraud compensation, reconciliation, Kafka projection
```

Once this message appears, the complete local workflow is operating.

## Step 13: Check actual memory usage

Run:

```powershell
py scripts/lite.py stats
```

This shows memory, CPU, and process counts for the project containers. Also open **Task Manager > Performance > Memory** and check total Windows memory usage.

For an 8 GB laptop:

- Close IntelliJ while running the first build and smoke test.
- Avoid running browsers with many tabs or other development tools simultaneously.
- Some disk activity is normal because Windows may use virtual memory.
- If a container repeatedly restarts or reports an out-of-memory exit, collect the diagnostic report described below.

## Step 14: Make a transfer manually

The application is a REST API, so opening `http://localhost:8080` directly does not display a webpage.

Generate a one-hour token for Alice:

```powershell
$token = py scripts/token.py alice
```

Create the request headers and body:

```powershell
$headers = @{
    Authorization = "Bearer $token"
    "Idempotency-Key" = [guid]::NewGuid().ToString()
}

$body = @{
    source = "00000000-0000-0000-0000-000000000001"
    destination = "00000000-0000-0000-0000-000000000002"
    amountMinor = 1000000
    currency = "INR"
} | ConvertTo-Json
```

`1000000` paise represents Rs 10,000.

Submit the payment:

```powershell
$payment = Invoke-RestMethod `
    -Uri "http://localhost:8080/api/payments" `
    -Method Post `
    -Headers $headers `
    -ContentType "application/json" `
    -Body $body

$payment
```

Copying the entire command block is recommended because the PowerShell backtick continues the command on the next line.

Check the payment status:

```powershell
Invoke-RestMethod `
    -Uri "http://localhost:8080/api/payments/$($payment.id)" `
    -Headers @{ Authorization = "Bearer $token" }
```

The first response may show an intermediate state. Wait a few seconds and run the status command again. The expected final state for this transfer is:

```text
COMPLETED
```

## Step 15: Inspect logs

View recent logs for all services:

```powershell
py scripts/lite.py logs
```

View one service only:

```powershell
py scripts/lite.py logs payment
py scripts/lite.py logs ledger
py scripts/lite.py logs kafka
```

The supported names are `postgres`, `redis`, `kafka`, `account`, `ledger`, `fraud`, `notification`, `payment`, and `gateway`.

## Step 16: Stop and restart the project

Stop the project while keeping all database and Kafka volumes:

```powershell
py scripts/lite.py stop
```

Restart without rebuilding the images when the source code has not changed:

```powershell
py scripts/lite.py start --skip-build
```

After changing Java code, rebuild and start:

```powershell
py scripts/lite.py start
```

The launcher stops this lite stack before rebuilding, which reduces peak memory usage. It does not delete the persisted data.

## Step 17: Troubleshoot a failed start

First check container status:

```powershell
py scripts/lite.py status
```

Create a diagnostic report:

```powershell
py scripts/lite.py report
```

This creates:

```text
lite-diagnostics.txt
```

The report contains service state, restart counts, out-of-memory indicators, and current container memory. It does not include the generated passwords from `.env`.

Then inspect the relevant logs:

```powershell
py scripts/lite.py logs
```

Common problems:

| Symptom | Action |
| --- | --- |
| Docker command cannot connect | Open Docker Desktop and wait for the engine to start |
| Docker reports Windows containers | Switch Docker Desktop to Linux containers using WSL 2 |
| `.env` is missing | Run `py scripts/init_env.py` |
| Port 8080 is already in use | Stop the other application or the original full banking project |
| A Java container exits with `oom=true` | Close IntelliJ and other applications, restart Docker, and retry |
| Kafka does not become healthy | Check `py scripts/lite.py logs kafka` and available memory |
| Payment remains in progress | Check the `payment`, `ledger`, and `fraud` logs |
| Start command fails after source changes | Run the normal `start` command without `--skip-build` |

When requesting help, share:

```powershell
py scripts/lite.py report
py scripts/lite.py status
```

Then provide `lite-diagnostics.txt` and the visible error message. Do not share `.env`.

## Step 18: Open the project again in IntelliJ

After the first build and smoke test pass, reopen IntelliJ IDEA and open the folder containing the root `pom.xml`.

Check these settings:

1. Go to **File > Project Structure > Project**.
2. Set the Project SDK to Java 21.
3. Go to **Settings > Build, Execution, Deployment > Build Tools > Maven**.
4. Select IntelliJ's bundled Maven.
5. Set the Maven runner JRE to the project JDK 21.

You can now inspect and edit the six service modules. When you change Java code and want to test the Docker version, close IntelliJ if memory is tight and run:

```powershell
py scripts/lite.py start
py scripts/lite.py test
```

## Step 19: Prepare the project for GitHub

Before creating a commit, verify that secrets and generated files are ignored:

```powershell
git status
```

Do not add these items:

```text
.env
build/
**/target/
lite-diagnostics.txt
```

The included `.gitignore` already covers them. Commit the source, `compose.yml`, Dockerfile, scripts, documentation, Maven files, tests, and GitHub Actions workflow.

## Command reference

| Task | Command |
| --- | --- |
| Check Docker | `py scripts/lite.py check` |
| Generate credentials | `py scripts/init_env.py` |
| First build/start | `py scripts/lite.py start` |
| Test the complete flow | `py scripts/lite.py test` |
| Check memory | `py scripts/lite.py stats` |
| Check status | `py scripts/lite.py status` |
| View all logs | `py scripts/lite.py logs` |
| View payment logs | `py scripts/lite.py logs payment` |
| Create diagnostic report | `py scripts/lite.py report` |
| Stop and preserve data | `py scripts/lite.py stop` |
| Restart existing images | `py scripts/lite.py start --skip-build` |
