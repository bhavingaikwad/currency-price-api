# ADR 3: AWS network without a NAT gateway

Status: accepted

## Context

The deployment had to run on free-plan credits and be cheap to leave up for a short demo. A NAT gateway adds an hourly charge plus a per-GB data charge. The app task needs outbound internet to pull its image and call the Frankfurter API.

## Decision

The VPC has two public and two private subnets across two zones and no NAT gateway. The load balancer and the Fargate task sit in the public subnets (the task has a public IP for outbound traffic). RDS and ElastiCache sit in private subnets whose route table has no route to the internet. Security groups form a chain: the load balancer accepts port 80 from the internet, the app accepts port 8080 only from the load balancer, and the database (5432) and cache (6379) accept traffic only from the app.

## Consequences

- No NAT gateway hourly or data charges while the stack ran.
- The task protects itself with its security group, not with network placement: it has a public IP, although nothing can reach port 8080 except the load balancer.
- Outbound traffic from the task is unrestricted. A production setup would use private subnets with VPC endpoints or a NAT gateway.
- The listener is plain HTTP; adding a domain and certificate is future work.
