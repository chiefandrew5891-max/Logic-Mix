FROM python:3.12-slim
ENV PYTHONUNBUFFERED=1
WORKDIR /app
# Ядро использует только stdlib; google-cloud-storage нужен для публикации в GCS.
COPY backend/requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY backend/logicmix_backend ./logicmix_backend
RUN useradd -m runner
USER runner
ENTRYPOINT ["python", "-m", "logicmix_backend.job"]
