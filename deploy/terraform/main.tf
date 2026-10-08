locals {
  name = "carddemo-${var.env}"
  services = ["run.googleapis.com", "sqladmin.googleapis.com", "redis.googleapis.com", "secretmanager.googleapis.com",
  "artifactregistry.googleapis.com", "vpcaccess.googleapis.com", "servicenetworking.googleapis.com"]
}

resource "google_project_service" "apis" {
  for_each           = toset(local.services)
  service            = each.value
  disable_on_destroy = false
}

resource "google_artifact_registry_repository" "carddemo" {
  repository_id = "carddemo"
  location      = var.region
  format        = "DOCKER"
  depends_on    = [google_project_service.apis]
}

# --- network: private IP for Cloud SQL and Memorystore, Direct VPC egress from Cloud Run -------------
resource "google_compute_network" "vpc" {
  name                    = "${local.name}-vpc"
  auto_create_subnetworks = false
}

resource "google_compute_subnetwork" "run" {
  name                     = "${local.name}-run"
  network                  = google_compute_network.vpc.id
  ip_cidr_range            = "10.20.0.0/24"
  region                   = var.region
  private_ip_google_access = true
}

resource "google_compute_global_address" "psa" {
  name          = "${local.name}-psa"
  purpose       = "VPC_PEERING"
  address_type  = "INTERNAL"
  prefix_length = 16
  network       = google_compute_network.vpc.id
}

resource "google_service_networking_connection" "psa" {
  network                 = google_compute_network.vpc.id
  service                 = "servicenetworking.googleapis.com"
  reserved_peering_ranges = [google_compute_global_address.psa.name]
}

# --- Cloud SQL for PostgreSQL (schema owned by Flyway in the API) --------------------------------------
resource "google_sql_database_instance" "pg" {
  name                = "${local.name}-pg"
  database_version    = "POSTGRES_16"
  region              = var.region
  deletion_protection = true
  depends_on          = [google_service_networking_connection.psa]
  settings {
    tier              = var.db_tier
    availability_type = var.env == "prod" ? "REGIONAL" : "ZONAL"
    ip_configuration {
      ipv4_enabled    = false
      private_network = google_compute_network.vpc.id
    }
    backup_configuration {
      enabled                        = true
      point_in_time_recovery_enabled = true
    }
    insights_config { query_insights_enabled = true }
  }
}

resource "google_sql_database" "carddemo" {
  name     = "carddemo"
  instance = google_sql_database_instance.pg.name
}

resource "random_password" "db" {
  length  = 32
  special = false
}

resource "google_sql_user" "app" {
  name     = "carddemo"
  instance = google_sql_database_instance.pg.name
  password = random_password.db.result
}

# --- Memorystore Redis (spring profile "redis": account / card view cache) -----------------------------
resource "google_redis_instance" "cache" {
  count              = var.redis_enabled ? 1 : 0
  name               = "${local.name}-cache"
  tier               = var.env == "prod" ? "STANDARD_HA" : "BASIC"
  memory_size_gb     = 1
  region             = var.region
  authorized_network = google_compute_network.vpc.id
  connect_mode       = "PRIVATE_SERVICE_ACCESS"
  redis_version      = "REDIS_7_2"
  depends_on         = [google_service_networking_connection.psa]
}

# --- Secret Manager ------------------------------------------------------------------------------------
resource "random_password" "jwt" {
  length  = 64
  special = false
}

resource "google_secret_manager_secret" "secret" {
  for_each  = { db-password = random_password.db.result, jwt-secret = random_password.jwt.result }
  secret_id = "${local.name}-${each.key}"
  replication {
    auto {}
  }
}

resource "google_secret_manager_secret_version" "secret" {
  for_each    = { db-password = random_password.db.result, jwt-secret = random_password.jwt.result }
  secret      = google_secret_manager_secret.secret[each.key].id
  secret_data = each.value
}

# --- service accounts ----------------------------------------------------------------------------------
resource "google_service_account" "api" {
  account_id   = "${local.name}-api"
  display_name = "carddemo online API"
}

resource "google_service_account" "ui" {
  account_id   = "${local.name}-ui"
  display_name = "carddemo online UI"
}

resource "google_secret_manager_secret_iam_member" "api" {
  for_each  = google_secret_manager_secret.secret
  secret_id = each.value.id
  role      = "roles/secretmanager.secretAccessor"
  member    = "serviceAccount:${google_service_account.api.email}"
}

resource "google_project_iam_member" "api_sql" {
  project = var.project_id
  role    = "roles/cloudsql.client"
  member  = "serviceAccount:${google_service_account.api.email}"
}
