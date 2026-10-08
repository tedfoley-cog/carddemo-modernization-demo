output "ui_url" { value = google_cloud_run_v2_service.ui.uri }
output "api_url" { value = google_cloud_run_v2_service.api.uri }
output "artifact_registry" {
  value = "${var.region}-docker.pkg.dev/${var.project_id}/${google_artifact_registry_repository.carddemo.repository_id}"
}
