variable "project_id" { type = string }
variable "region" {
  type    = string
  default = "europe-west2"
}
variable "env" {
  type    = string
  default = "dev"
}
variable "api_image" {
  type        = string
  description = "Artifact Registry image for carddemo-online, e.g. <region>-docker.pkg.dev/<project>/carddemo/online:<sha>"
}
variable "ui_image" { type = string }
variable "db_tier" {
  type    = string
  default = "db-custom-2-7680"
}
variable "api_min_instances" {
  type    = number
  default = 1
}
variable "api_max_instances" {
  type    = number
  default = 20
}
variable "redis_enabled" {
  type    = bool
  default = true
}
