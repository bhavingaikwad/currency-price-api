# Runbook

Region us-east-2. Terraform lives in `infra/` and uses the AWS profile `currency-api` (variable `aws_profile`). The AWS CLI is not needed. Spend comes out of free-plan credits and a $5 budget alert exists; destroy the stack after every use.

## Deploy to AWS

1. Create the infrastructure (about 10 minutes; RDS takes about 5 and ElastiCache about 8). The service cannot start tasks yet because the image is not in ECR, which is expected:

```bash
cd infra && terraform init && terraform apply
```

2. Log in to the registry (no AWS CLI needed), then build and push an ARM64 image tagged with the commit:

```bash
terraform output -raw ecr_password | docker login --username AWS --password-stdin "$(terraform output -raw ecr_registry)"
cd ..
TAG=$(git rev-parse --short HEAD)
REPO=$(cd infra && terraform output -raw ecr_repo_url)
docker build --platform linux/arm64 -t "$REPO:$TAG" .
docker push "$REPO:$TAG"
```

3. Point the service at the new image. Always pass your own tag: the default `image_tag` in `variables.tf` is an old commit hash that does not exist in a fresh registry.

```bash
cd infra && terraform apply -var image_tag="$TAG"
```

4. Verify after 1 to 2 minutes (Fargate startup took about 24 s; the health-check grace period is 120 s):

```bash
URL=$(terraform output -raw app_url)
curl "$URL/actuator/health/readiness"
terraform output -raw reader_password   # password for POST $URL/api/v1/auth/token
```

5. Load data: log in as `admin` (`terraform output -raw admin_password`) and call `POST $URL/api/v1/admin/ingest?provider=ECB` with `{"base":"USD","quotes":["EUR","GBP","JPY"]}`. Calling it again stores 0 new rows.

## Tear down

Run `cd infra && terraform destroy` and type `yes` (about 5 to 10 minutes). Then check that `terraform state list` prints nothing and that the app URL no longer answers. Billing data lags by about a day, so read the Billing page the next day.

## When something is wrong

- Targets unhealthy or tasks restarting: read the CloudWatch log group `/ecs/currency-price-api` (JSON lines). The load balancer checks `/actuator/health/readiness`.
- 401: token missing or older than 30 minutes. 403: a reader token on an admin route. 429: wait `Retry-After` seconds.
- Redis down: the API keeps answering from PostgreSQL and `rates.cache{result=error}` increases.
- Roll back: `terraform apply -var image_tag=<previous tag>`.
- The dashboard and the alarms `currency-api-5xx` and `currency-api-no-healthy-hosts` are created by the next apply; they have not been deployed yet.

## Local

`docker compose up -d --build` starts the app, PostgreSQL and Redis. For load tests start the stack with `docker compose -f docker-compose.yml -f docker-compose.loadtest.yml up -d` and run `./scripts/loadtest.sh BINANCE 10 30s`.
