# carddemo-online: stateless Spring Boot API. Internal ingress; only the UI service (and the load balancer
# in front of it) can reach it.
resource "google_cloud_run_v2_service" "api" {
  name     = "${local.name}-online-api"
  location = var.region
  ingress  = "INGRESS_TRAFFIC_INTERNAL_ONLY"

  template {
    service_account                  = google_service_account.api.email
    max_instance_request_concurrency = 80
    scaling {
      min_instance_count = var.api_min_instances
      max_instance_count = var.api_max_instances
    }
    vpc_access {
      egress = "PRIVATE_RANGES_ONLY"
      network_interfaces {
        network    = google_compute_network.vpc.id
        subnetwork = google_compute_subnetwork.run.id
      }
    }
    containers {
      image = var.api_image
      resources {
        limits            = { cpu = "2", memory = "2Gi" }
        startup_cpu_boost = true
      }
      env {
        name  = "SPRING_PROFILES_ACTIVE"
        value = var.redis_enabled ? "redis" : "default"
      }
      env {
        name  = "CARDDEMO_DB_URL"
        value = "jdbc:postgresql://${google_sql_database_instance.pg.private_ip_address}:5432/carddemo"
      }
      env {
        name  = "CARDDEMO_DB_USER"
        value = google_sql_user.app.name
      }
      env {
        name = "CARDDEMO_DB_PASSWORD"
        value_source {
          secret_key_ref {
            secret  = google_secret_manager_secret.secret["db-password"].secret_id
            version = "latest"
          }
        }
      }
      env {
        name = "CARDDEMO_JWT_SECRET"
        value_source {
          secret_key_ref {
            secret  = google_secret_manager_secret.secret["jwt-secret"].secret_id
            version = "latest"
          }
        }
      }
      dynamic "env" {
        for_each = var.redis_enabled ? [1] : []
        content {
          name  = "REDIS_HOST"
          value = google_redis_instance.cache[0].host
        }
      }
      startup_probe {
        http_get { path = "/actuator/health/readiness" }
        period_seconds    = 5
        failure_threshold = 24
      }
      liveness_probe {
        http_get { path = "/actuator/health/liveness" }
      }
    }
  }
  depends_on = [google_secret_manager_secret_iam_member.api]
}

# carddemo-ui: nginx serving the React build and proxying /api to the API service.
resource "google_cloud_run_v2_service" "ui" {
  name     = "${local.name}-online-ui"
  location = var.region
  ingress  = "INGRESS_TRAFFIC_ALL"

  template {
    service_account = google_service_account.ui.email
    vpc_access {
      egress = "ALL_TRAFFIC"
      network_interfaces {
        network    = google_compute_network.vpc.id
        subnetwork = google_compute_subnetwork.run.id
      }
    }
    containers {
      image = var.ui_image
      resources {
        limits = { cpu = "1", memory = "512Mi" }
      }
      env {
        name  = "API_URL"
        value = google_cloud_run_v2_service.api.uri
      }
    }
  }
}

resource "google_cloud_run_v2_service_iam_member" "ui_invokes_api" {
  name     = google_cloud_run_v2_service.api.name
  location = var.region
  role     = "roles/run.invoker"
  member   = "serviceAccount:${google_service_account.ui.email}"
}
