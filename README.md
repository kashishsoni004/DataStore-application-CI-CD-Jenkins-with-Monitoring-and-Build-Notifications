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
- Java 21
- Maven
- Docker
- AWS CLI
- GitHub repository
- DockerHub account
- Slack App (Bot Token based)
- Kubernetes cluster with ArgoCD

---

## Step-by-Step Setup

### Step 1: Create Server

- Launch an EC2 instance (medium size)

---

### Step 2: Install Java & Git 

    sudo yum install git -y
    sudo yum install java-21-amazon-corretto -y

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
  - Slack Notification 

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
                sh """
                    echo "-------- Building Application --------"
                    mvn clean package
                    echo "------- Application Built Successfully --------"
                """
            }
        }

---

### Test Stage

    stage("Maven Test") {
            steps {
                sh """
                    echo "-------- Executing Testcases --------"
                    mvn test
                    echo "-------- Testcases Execution Complete --------"
                """
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
                sh """
                    echo "-------- Pushing Artifacts To S3 --------"
                    aws s3 cp ./target/*.jar s3://datastore-artefact-store-jenkins-apps-apppp/
                    echo "-------- Pushing Artifacts To S3 Completed --------"
                """
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
                sh """
                    echo "-------- Building Docker Image --------"
                    docker build -t datastore:"${App_Version}" .
                    echo "-------- Image Successfully Built --------"
                """
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

- Type: Username with password
- ID: dockerhub

---

### Tag Image

     stage("Docker Image Tag") {
            steps {
                sh """
                    echo "-------- Tagging Docker Image --------"
                    docker tag datastore:"${App_Version}" bhawnavishwakarma/datastore:"${App_Version}"
                    echo "-------- Tagging Docker Image Completed --------"
                """
            }
        }

---

### Push Image

    stage("Loggingin & Pushing Docker Image") {
            steps {
                sh """
                    echo "-------- Logging To DockerHub --------"
                    docker login -u $DOCKERHUB_CREDENTIALS_USR --password $DOCKERHUB_CREDENTIALS_PSW
                    echo "-------- DockerHub Login Successful --------"

                    echo "-------- Pushing Docker Image To DockerHub --------"
                    docker push bhawnavishwakarma/datastore:"${App_Version}"
                    echo "-------- Docker Image Pushed Successfully --------"
                """
            }
        }

---

### Cleanup

    stage("Cleanup") {
            steps {
                sh """
                    echo "-------- Cleaning Up Jenkins Machine --------"
                    docker image prune -a -f
                    echo "-------- Clean Up Successful --------"
                """
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
        GITHUB_TOKEN = credentials('github-token')
    }

    parameters {
        string(
            name: 'App_Name',
            description: 'application name that need to be deployed'
        )
        string(
            name: 'App_Version',
            description: 'version of the application that need to be deployed'
        )
    }

    stages {

        stage('Checkout') {
            steps {
                checkout scmGit(
                    branches: [[name: '*/main']],
                    extensions: [],
                    userRemoteConfigs: [[
                        url: 'https://github.com/kashishsoni004/Kubernetes-ArgoCD.git'
                    ]]
                )
            }
        }

        stage('Update Files') {
            steps {
                sh """
                    echo "-------- Updating File Content --------"

                    cd ${params.App_Name}

                    sed -i 's|image:.*|image: kashishsoni004/datastore:${params.App_Version}|g' ${params.App_Name}.yaml

                    echo "-------- File Content Updated Successfully --------"
                """
            }
        }

        stage('GitHub Push') {
            steps {
                sh """
                    echo "-------- Pushing Changes To GitHub --------"

                    git config user.name "jenkins"
                    git config user.email "jenkins@local"

                    git add .
                    git commit -m "docker image updated" || echo "No changes to commit"

                    git push https://${GITHUB_TOKEN}@github.com/kashishsoni004/Kubernetes-ArgoCD.git HEAD:main

                    echo "-------- Pushed Changes Successfully --------"
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

## 🔔 Slack Notifications Integration

 Step 1: Create Slack App
       Go to: https://api.slack.com/apps
Click Create New App
Choose:
      From Scratch
App Name:
        jenkins
Select your workspace:
      awspracticeco
Step 2: Add Bot Permissions

Go to:

OAuth & Permissions → Bot Token Scopes

Add:

    chat:write
    chat:write.public
    channels:read

Step 3: Install App

Click:

Install to Workspace

👉 Copy Bot Token:

    xoxb-xxxxxxxx
    
Step 4: Add Credential in Jenkins

Go to:

Manage Jenkins → Credentials → Global

Add:

Kind: Secret Text
    Secret: Slack Bot Token
     ID: slackSend
     
Step 5: Configure Slack in Jenkins

Go to:

Manage Jenkins → System → Slack

     Field	Value
    Workspace	awspracticeco
    Credential	slackSend
    Open channel → copy Link:

Example:

     https://app.slack.com/client/TXXXX/C0AMPGQ5N9Y

👉 Use:

    C0AMPGQ5N9Y

   Enable Custom Slack Bot 

👉 Save

Step 6: Create Slack Channel
Open your Slack workspace:
    👉 awspracticeco.slack.com
In left sidebar → click ➕ (Add channels)
Click:
Create a new channel
Enter details:
Channel name: practise
Type: Public

👉 Final channel:

     #practise
Click Create

🔄 Step 7: Invite Bot to Channel

After creating channel:

     /invite @jenkins
     

### Step 7: Pipeline Integration

    post {
        success {
            slackSend(
                channel: '#practice',
                color: 'good',
                message: "SUCCESS: ${env.JOB_NAME} #${env.BUILD_NUMBER} completed successfully"
            )
        }

        failure {
            slackSend(
                channel: '#practice',
                color: 'danger',
                message: "FAILURE: ${env.JOB_NAME} #${env.BUILD_NUMBER} failed\n${env.BUILD_URL}"
            )
        }

        unstable {
            slackSend(
                channel: '#practice',
                color: 'warning',
                message: "UNSTABLE: ${env.JOB_NAME} #${env.BUILD_NUMBER}"
            )
        }

        aborted {
            slackSend(
                channel: '#practice',
                color: 'warning',
                message: "ABORTED: ${env.JOB_NAME} #${env.BUILD_NUMBER}"
            )
        }

        always {
            slackSend(
                channel: '#practice',
                color: 'good',
                message: "Build finished: ${env.JOB_NAME} #${env.BUILD_NUMBER}"
            )
        }
    }

---

## 🔄 Final Workflow

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

## ✅ Final Status

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
---
