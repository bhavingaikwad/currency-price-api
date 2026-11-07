resource "aws_elasticache_subnet_group" "main" {
  name       = "currency-api"
  subnet_ids = aws_subnet.private[*].id
}

resource "aws_elasticache_replication_group" "main" {
  replication_group_id       = "currency-api"
  description                = "currency-api cache"
  engine                     = "valkey"
  engine_version             = "8.0"
  node_type                  = "cache.t4g.micro"
  num_cache_clusters         = 1
  parameter_group_name       = "default.valkey8"
  port                       = 6379
  subnet_group_name          = aws_elasticache_subnet_group.main.name
  security_group_ids         = [aws_security_group.cache.id]
  automatic_failover_enabled = false
}

output "cache_endpoint" {
  value = aws_elasticache_replication_group.main.primary_endpoint_address
}
