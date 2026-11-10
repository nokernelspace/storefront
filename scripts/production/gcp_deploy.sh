
# Reminder that Cloud Run automatically maps port 8080 to webserver functionality (SSL)
gcloud run deploy my-service \
  --image gcr.io/my-project/my-image:latest \
  --region us-central1 \
  --allow-unauthenticated