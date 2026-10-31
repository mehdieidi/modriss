FROM golang:1.25-bookworm AS go
FROM python:3.13-slim-bookworm
COPY --from=go /usr/local/go /usr/local/go
COPY --from=go /usr/bin/make /usr/bin/make
ENV PATH="/usr/local/go/bin:${PATH}" CGO_ENABLED=0 PYTHONUNBUFFERED=1
RUN pip install --no-cache-dir boto3==1.40.70 PyYAML==6.0.3 awscli==1.42.70 aws-sam-translator==1.116.0 \
    && useradd --uid 10001 --create-home runner
COPY runner.py /runner/runner.py
COPY template_validation.py /runner/template_validation.py
USER runner
WORKDIR /tmp
ENTRYPOINT ["python", "/runner/runner.py"]
