# CI/CD + GitOps Pipeline Setup using Jenkins

---

## Overview

This project demonstrates a complete CI/CD pipeline integrated with GitOps using Jenkins, Docker, AWS, and Kubernetes.

The pipeline automates:
- Code checkout
- Build → Test → SonarQube Analysis → Quality Gate
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
- SonarQube 
- Docker
- Trivy
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
  - SonarQube Scanner
  - Quality Gates

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
                        userRemoteConfigs: [[url: 'https://github.com/kashishsoni004/DataStore-application-CI-CD-Jenkins-with-Monitoring-and-Build-Notifications.git']]
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

### ⚙️ SonarQube Setup

#### Run SonarQube using Docker

    docker run -d --name sonar -p 9000:9000 sonarqube

Access:

    http://< your-server-ip >:9000

---

### 🔑 Generate Token

- Login: admin / admin  
- Go to My Account → Security  
- Generate token  

---

### 🔐 Add Credentials in Jenkins

- Type: Secret Text  
- ID: sonar-token  

---

### ⚙️ Configure SonarQube in Jenkins

- Manage Jenkins → Configure System  

Add SonarQube Server:

- Name: sonar  
- URL: http://< your-server-ip >:9000  
- Credentials: sonar-token  

---

### 🚀 Pipeline Stage (SonarQube Analysis)

    stage("SonarQube Analysis") {
    steps {
        withSonarQubeEnv('sonar') {
            sh """
                mvn clean verify sonar:sonar \
                -Dsonar.projectKey=datastore
            """
        }
      }   
    }

---

### ✅ Quality Gate (optional)

    stage("Quality Gate") {
        steps {
            timeout(time: 2, unit: 'MINUTES') {
                waitForQualityGate abortPipeline: true  #false
            }
        }
    }

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

    datastore-artefact-store-jenkins-apps-appppp

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
                    aws s3 cp ./target/*.jar s3://datastore-artefact-store-jenkins-apps-appppp/
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

### ⚙️ Trivy Scan Stage

       stage("Docker Scan (Trivy)") {
         steps {
           sh '''
            echo "-------- Scanning Image --------"

            export TRIVY_TEMP_DIR=/var/lib/jenkins/trivy-temp
            mkdir -p $TRIVY_TEMP_DIR

            trivy image \
              --scanners vuln \
              --timeout 5m \
              --skip-java-db-update \
              datastore:${App_Version} || true

            echo "-------- Scan Completed --------"
        '''
           }
       }

## DockerHub Integration

### Repository

    kashishsoni004/datastore

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
                    docker tag datastore:"${App_Version}" kashishsoni004/datastore:"${App_Version}"
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
                    docker push kashishsoni004/datastore:"${App_Version}"
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

### GitHub Token add credentials inside jenkins

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
        string(
            name: "App_Name",
            description: "application name that need to be deployed"
        )
        string(
            name: "App_Version",
            description: "version of the application that need to be deployed"
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

### Step 1: Install Plugin

- Manage Jenkins → Manage Plugins  
- Install: Slack Notification  

---

### Step 2: Create Slack App

- Go to Slack API  
- Create app from scratch  
- Select workspace  

---

### Step 3: Configure OAuth & Permissions

Add Bot Token Scopes:

- chat:write  
- chat:write.public  
- incoming-webhook  

---

### Step 4: Install App

- Click **Install to Workspace**  
- Copy Bot Token  

---

### Step 5: Configure Jenkins

- Manage Jenkins → Configure System → Slack  

Fill:

- Workspace (aws practise) 
- Credentials (Bot Token)
- - Type: Secret Text
- - - Secret: bot token paste
- - - ID: slackSend
- Default Channel  (copy your channel link and paste )
- Enable Custom Slack Bot  

---

### Step 6: Invite Bot

    /invite @your-bot-name (jenkins)

---

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

## 🚀 ArgoCD Setup (GitOps Deployment)
✅ Step 1: Make sure Kubernetes is ready

 You must have:

    A running Kubernetes cluster (EKS / Minikube / K8s on EC2)
    kubectl configured

#### Pre-requisites: 
  - an EC2 Instance
---

#### AWS EKS Setup 
### 1. Setup kubectl   
   a. Download kubectl version 1.20  
   b. Grant execution permissions to kubectl executable   
   c. Move kubectl onto /usr/local/bin   
   d. Test that your kubectl installation was successful    
   ```sh 
   curl -o kubectl https://amazon-eks.s3.us-west-2.amazonaws.com/1.19.6/2021-01-05/bin/linux/amd64/kubectl
   chmod +x ./kubectl
   mv ./kubectl /usr/local/bin 
   kubectl version --short --client
   ```
--- 
### 2. Setup eksctl   
   a. Download and extract the latest release   
   b. Move the extracted binary to /usr/local/bin   
   c. Test that your eksclt installation was successful   
   ```sh
   curl --silent --location "https://github.com/weaveworks/eksctl/releases/latest/download/eksctl_$(uname -s)_amd64.tar.gz" | tar xz -C /tmp
   sudo mv /tmp/eksctl /usr/local/bin
   eksctl version
   ```
---
  
### 3. Create an IAM Role and attache it to EC2 instance    
   `Note: create IAM user with programmatic access if your bootstrap system is outside of AWS`   
    You need to attach proper IAM permissions to your EC2 instance role.

  🔧 Step 1: Go to AWS Console
     - Open Amazon Web Services
     Go to IAM → Roles
     - Find your role: ec2-admin
     
  🔧 Step 2: Attach required policies

   Add these policies:

  ✅ Required for EKS
   - AmazonEKSClusterPolicy
   - AmazonEKSWorkerNodePolicy
   - AmazonEKS_CNI_Policy
     
  ✅ Required for EC2 + networking
   - AmazonEC2FullAccess (for learning/demo)
   
---
### 4. Create your cluster and nodes 
   ```sh
   eksctl create cluster --name cluster-name  \
   --region region-name \
   --node-type instance-type \
   --nodes-min 2 \
   --nodes-max 2 \ 
   --zones <AZ-1>,<AZ-2>
   
   example:
   eksctl create cluster --name mahima \
      --region us-east-1 \
   --node-type t2.small \
```
---

### 5. To delete the EKS clsuter (if uhh want to delete)
   ```sh
      eksctl delete cluster mahima --region ap-south-1
   ```
---   

### 6. Validate your cluster using by creating by checking nodes and by creating a pod 
   ```sh 
   kubectl get nodes
   ```
---

✅ Step 2: Create the namespace for argoCD
   ```sh 
     kubectl create namespace argocd
   ```
---

✅ Step 3: Install ArgoCD using the below command
   ```sh 
   kubectl apply -n argocd -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml
   ```
---

✅ Step 3: check what resources it has created
 ```sh 
    kubectl get all -n argocd
 ```

 ```sh 
    kubectl get all -n argocd
 ```

 ```sh 
    kubectl edit svc argocd-server -n argocd  #enable NodePort 
 ```

 ```sh
    kubectl get nodes -n argocd
 ```

 ```sh
    kubectl get svc -n argocd
 ```
---

✅ Step 4: Access ArgoCD UI
 - take any instance public ip and hit browser

Open in browser:
```sh 
   https://<YOUR-EC2-IP>:<NODEPORT ip>
```
---

✅ Step 5: Get login password

```sh 
   kubectl get secret argocd-initial-admin-secret -n argocd -o yaml
```
- the secret base64 encoded so, you have to decod the secret by runnig the below command
```sh
   echo "secret value" | base64 --decode
```
   
Login:
   - Username: admin
   - Password: (command output)
---

✅ Step 6: Connect your GitOps repository
    - This should be your repo where Kubernetes YAML files exist
    (the same repo Jenkins updates using sed)
---

✅ Step 7: Create Application in ArgoCD

   In ArgoCD UI:

   General:
   
   - Application Name: datastore-app
   - Project: default
   - sync policy: Automatic
     
   Source:
   
   - Repo URL: your GitOps repo URL
   - Branch: main
   - Path: folder where YAML exists (e.g. datastore-deploy)
     
   Destination:
   
   - Cluster: https://kubernetes.default.svc
   - Namespace: default (or your custom namespace)
   ```sh
       kubectl create ns dev
   ```
  Create 
---

✅ Step 8: Enable Auto Sync

   Enable:
   
  - ✅ Auto Sync
  - ✅ Self Heal
---

✅ Step 9: Deploy Application

   Click:
   
  - Create → Sync
    
Verify Deployment
 ```sh
       kubectl get pods
 ```
---

 ## 🔄 Final Workflow

    Code Push → GitHub  
        ↓  
    Jenkins CI Pipeline Triggered  
        ↓  
    Build & Test  
        ↓  
    SonarQube Analysis (Code Quality Check)  
        ↓  
    Quality Gate Validation  
        ↓  
    Artifact Uploaded to S3  
        ↓  
    Docker Image Built  
        ↓  
    Trivy Scan (Security Check)  
        ↓  
    Docker Image Pushed to DockerHub  
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
- SonarQube integrated for code quality analysis  
- Quality gate enforced before deployment  
- Trivy integrated for vulnerability scanning  
- Downstream pipeline working  
- GitHub token authentication configured  
- GitOps deployment enabled  
- Slack notifications integrated  
- Fully automated DevSecOps pipeline implemented
- End-to-end automated CI/CD pipeline with security and quality checks

---
---
