FROM python:3.12-slim
WORKDIR /app
COPY backend/requirements.txt backend/requirements.txt
COPY backend/requirements-postgres.txt backend/requirements-postgres.txt
RUN pip install --no-cache-dir -r backend/requirements.txt -r backend/requirements-postgres.txt
COPY backend backend
COPY frontend frontend
COPY config config
ENV PYTHONPATH=/app/backend
EXPOSE 8000
CMD ["python", "-m", "uvicorn", "jiradash.main:app", "--host", "0.0.0.0", "--port", "8000"]
