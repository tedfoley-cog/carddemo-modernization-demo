terraform {
  required_version = ">= 1.6"
  required_providers {
    google = { source = "hashicorp/google", version = "~> 5.40" }
    random = { source = "hashicorp/random", version = "~> 3.6" }
  }
  # Configure per environment, e.g. terraform init -backend-config="bucket=<state-bucket>"
  backend "gcs" { prefix = "carddemo/online" }
}

provider "google" {
  project = var.project_id
  region  = var.region
}
