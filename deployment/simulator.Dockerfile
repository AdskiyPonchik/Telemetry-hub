FROM python:3.13-alpine

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    TZ=UTC

WORKDIR /app

COPY requirements-simulator.txt .
RUN pip install --no-cache-dir --requirement requirements-simulator.txt

COPY simulator.py .

USER 65532:65532

ENTRYPOINT ["python", "simulator.py"]
