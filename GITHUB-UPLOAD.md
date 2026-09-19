# Upload this project to GitHub

Extract the ZIP. The folder named `banking-ledger-lite` is the repository root; it contains `README.md`, `pom.xml`, `compose.yml`, `.gitignore`, `.github`, the service modules, scripts, tests, infrastructure files, and documentation.

## Recommended: upload with Git

Create an empty repository on GitHub. Do not initialize it with a README, `.gitignore`, or license, because those files are already included here.

Open PowerShell inside the extracted `banking-ledger-lite` folder:

```powershell
git init
git branch -M main
git add .
git status
git commit -m "Initial commit: distributed banking ledger lite"
git remote add origin https://github.com/YOUR_USERNAME/YOUR_REPOSITORY.git
git push -u origin main
```

Replace `YOUR_USERNAME` and `YOUR_REPOSITORY` with your GitHub details.

Before committing, confirm that `git status` does not show these generated items:

```text
.env
build/
target/
lite-diagnostics.txt
```

They are covered by `.gitignore`. Never force-add `.env`.

## GitHub website upload

You can also create an empty repository, choose **uploading an existing file**, and drag the contents of the extracted `banking-ledger-lite` folder into the page. Upload the folder contents so that `README.md` appears at the repository root. Git is recommended because it preserves the complete directory structure and makes future updates easier.

## After uploading

Open the repository page and verify:

1. GitHub renders `README.md` on the main page.
2. `.github/workflows/ci.yml` is present.
3. The Actions tab contains the `verify-lite` workflow.
4. `.env` is absent.
5. The six service folders and `common` are present.

The first GitHub Actions run builds and tests the project on GitHub infrastructure. A failed workflow does not affect the working containers on your laptop; open the failed step to review its logs.
