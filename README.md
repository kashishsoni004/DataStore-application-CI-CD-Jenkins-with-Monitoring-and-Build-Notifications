# CI/CD + GitOps Pipeline Setup using Jenkins

---

## Overview

This project demonstrates a complete CI/CD pipeline integrated with GitOps using Jenkins, Docker, AWS, and Kubernetes.

The pipeline automates:
- Code checkout
- Build and test
- Artifact storage (S3)
- Docker image creation and push
- Security scanning
- Deployment using GitOps (ArgoCD)
- Slack notifications

Source reference: :contentReference[oaicite:0]{index=0}

---

## Architecture

    Developer → GitHub → Jenkins CI Pipeline → S3 Artifact Store
                                          → Docker Build & Push → DockerHub
                                          → Manual Approval
                                          → Downstream Pipeline
                                          → GitOps Repo Update → Kubernetes (ArgoCD)

---

## Prerequisites

- AWS EC2 instance (Amazon Linux / RHEL-based)
- Jenkins
- Java 17
- Maven
- Docker
- AWS CLI
- GitHub repository
- DockerHub account
- Slack Webhook
- Kubernetes cluster with ArgoCD

---

## Step-by-Step Setup

### Step 1: Create Server

- Launch an EC2 instance (medium size)

---

### Step 2: Install Java

    sudo yum install java-17-amazon-corretto.x86_64 -y

---

### Step 3: Install Jenkins

    sudo wget -O /etc/yum.repos.d/jenkins.repo https://pkg.jenkins.io/redhat-stable/jenkins.repo
    sudo rpm --import https://pkg.jenkins.io/redhat-stable/jenkins.io-2023.key
    sudo yum install jenkins -y

---

### Step 4: Start Jenkins

    sudo systemctl enable jenkins
    sudo systemctl start jenkins

---

### Step 5: Access Jenkins

- Open browser:
  
      http://<your-server-ip>:8080

- Install plugins:
  - Stage View
  - Blue Ocean
  - Parameterized Trigger

---

## Jenkins Pipeline (CI)

### Checkout Stage

    pipeline {
        agent any
        stages {
            stage ("Checkout"){
                steps {
                    checkout scmGit(
                        branches: [[name: '*/main']],
                        userRemoteConfigs: [[url: 'https://github.com/bhawnavishwakarma007/DataStore.git']]
                    )
                }
            } 
        }
    }

---

### Install Maven

    sudo yum install maven -y

---

### Build Stage

    stage("Maven Build") {
        steps {
            sh "mvn clean package"
        }
    }

---

### Test Stage

    stage("Maven Test") {
        steps {
            sh "mvn test"
        }
    }

---

## AWS Setup

### Install AWS CLI

    sudo yum update -y
    sudo yum install -y unzip curl

    curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o "awscliv2.zip"
    unzip awscliv2.zip
    sudo ./aws/install

---

### S3 Bucket

- Create bucket:

    datastore-artefact-store-jenkins-apps-apppp

---

### IAM Role

- Attach policy:
  - AmazonS3FullAccess

- Attach role to EC2 instance

---

### Artifact Upload Stage

    stage("Artifact Store") {
        steps {
            sh "aws s3 cp ./target/*.jar s3://datastore-artefact-store-jenkins-apps-apppp/"
        }
    }

---

## Docker Setup

### Install Docker

    sudo yum install docker -y

---

### Start Docker

    sudo systemctl start docker
    sudo usermod -aG docker jenkins
    sudo systemctl restart jenkins

---

### Docker Build Stage

    stage("Docker Image Build") {
        steps {
            sh "docker build -t datastore:${App_Version} ."
        }
    }

---

### Fix Docker Error

Error:
    
    ./mvnw: Permission denied

Fix:

    RUN chmod +x mvnw

---

## Security Scanning (Trivy)

    LATEST=$(curl -s https://api.github.com/repos/aquasecurity/trivy/releases/latest | grep tag_name | cut -d '"' -f 4)
    wget https://github.com/aquasecurity/trivy/releases/download/${LATEST}/trivy_${LATEST#v}_Linux-64bit.rpm
    sudo rpm -ivh trivy_${LATEST#v}_Linux-64bit.rpm

---

## DockerHub Integration

### Repository

    bhawnavishwakarma/datastore

---

### Add Credentials in Jenkins

- ID: dockerhub

---

### Tag Image

    stage("Docker Image Tag") {
        steps {
            sh "docker tag datastore:${App_Version} bhawnavishwakarma/datastore:${App_Version}"
        }
    }

---

### Push Image

    stage("Docker Push") {
        steps {
            sh """
            docker login -u $DOCKERHUB_CREDENTIALS_USR --password $DOCKERHUB_CREDENTIALS_PSW
            docker push bhawnavishwakarma/datastore:${App_Version}
            """
        }
    }

---

### Cleanup

    stage("Cleanup") {
        steps {
            sh "docker image prune -a -f"
        }
    }

---

## Manual Approval

    stage("Deployment Acceptance") {
        steps {
            input 'Trigger Down Stream Job??'
        }
    }

---

## Downstream Pipeline (GitOps)

### Pipeline Name

    KubernetesDeployment

---

### GitHub Token

- Type: Secret Text
- ID: github-token

---

### Pipeline Code

    pipeline {
        agent any

        environment {
            GITHUB_TOKEN = credentials("github-token")
        }

        parameters {
            string(name: "App_Name")
            string(name: "App_Version")
        }

        stages {

            stage("Checkout") {
                steps {
                    checkout scmGit(
                        branches: [[name: '*/main']],
                        userRemoteConfigs: [[url: 'https://github.com/bhawnavishwakarma007/Kubernetes-ArgoCD.git']]
                    )
                }
            }

            stage("Update Files") {
                steps {
                    sh """
                    cd ${params.App_Name}
                    sed -i 's/image:.*/image: bhawnavishwakarma\\/datastore:${params.App_Version}/g' ${params.App_Name}.yaml
                    """
                }
            }

            stage("GitHub Push") {
                steps {
                    sh """
                    git add .
                    git commit -am "image updated" || echo "No changes"
                    git push https://$GITHUB_TOKEN@github.com/bhawnavishwakarma007/Kubernetes-ArgoCD.git HEAD:main
                    """
                }
            }
        }
    }

---

### Trigger Downstream Pipeline

    stage("Triggering Deployment") {
        steps {
            build job: "KubernetesDeployment",
            parameters: [
                string(name: "App_Name", value: "datastore-deploy"),
                string(name: "App_Version", value: "${params.App_Version}")
            ]
        }
    }

---

## Slack Notifications

    post {
        success {
            slackSend channel: '#alerting', color: 'good',
            message: "SUCCESS: ${env.JOB_NAME} #${env.BUILD_NUMBER}"
        }
        failure {
            slackSend channel: '#alerting', color: 'danger',
            message: "FAILURE: ${env.BUILD_URL}"
        }
    }

---

## Final Workflow

    Code Push → GitHub
        ↓
    Jenkins CI Pipeline Triggered
        ↓
    Build & Test
        ↓
    Artifact Uploaded to S3
        ↓
    Docker Image Built & Pushed
        ↓
    Manual Approval
        ↓
    Downstream Pipeline Triggered
        ↓
    YAML Updated (GitOps Repo)
        ↓
    Kubernetes Deployment via ArgoCD

---

## Final Status

- CI/CD pipeline successfully implemented
- DockerHub integration completed
- IAM role configured securely
- Docker build issues resolved
- Trivy installed for vulnerability scanning
- Downstream pipeline working
- GitHub token authentication configured
- GitOps deployment enabled
- Slack notifications integrated

---
