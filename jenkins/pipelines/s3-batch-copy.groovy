def getAccountNumber(accountName) {
    def accountMap = [
        'aws': '272117124614',
        'liman': '272117124614'
    ]
    return accountMap[accountName] ?: null
}

// Resource prefix matches Terraform project_name - update if infrastructure changes
def RESOURCE_PREFIX = 's3-batch-operations-liman'


pipeline {
    agent any
    
    options {
        buildDiscarder(logRotator(numToKeepStr: '10'))
        timestamps()
    }
    
    parameters {
        string(name: 'ACCOUNT_NAME', defaultValue: '', description: 'Account Name (e.g., aws) - Used only for account number mapping')
        string(name: 'ENV_TAG', defaultValue: '', description: 'Environment tag value to filter buckets (e.g., dev, staging, prod)')
        string(name: 'SOURCE_PREFIX', defaultValue: '', description: 'Bucket prefix to copy from')
        string(name: 'DEST_PREFIX', defaultValue: '', description: 'Bucket prefix to copy to')
        string(name: 'REGION', defaultValue: 'us-east-1', description: 'AWS Region')
        string(name: 'PRIORITY', defaultValue: '2', description: 'Job priority (higher number = higher priority)')
    }
    
    environment {
        OPERATION_TAG = 's3BatchOperations'
        ACCOUNT_NUMBER = "${getAccountNumber(params.ACCOUNT_NAME)}"
        NAME_PREFIX = "${RESOURCE_PREFIX}-${params.ENV_TAG}"
        S3_BATCH_INFRA_ROLE_NAME = "${env.NAME_PREFIX}-s3-batch-infra-role"
        S3_BATCH_INFRA_ROLE_ARN = "arn:aws:iam::${env.ACCOUNT_NUMBER}:role/${env.S3_BATCH_INFRA_ROLE_NAME}"
        REPORT_BUCKET = "${env.NAME_PREFIX}-report-${params.ENV_TAG}"
        MANIFEST_BUCKET = "${env.NAME_PREFIX}-manifest-${params.ENV_TAG}"
        BATCH_JOB_ROLE_NAME = "${env.NAME_PREFIX}-batch-job-role"
    }
    
    
    stages {
        stage('Validate Parameters') {
            steps {
                script {
                    if (!params.ACCOUNT_NAME || !params.ENV_TAG) {
                        error('ACCOUNT_NAME and ENV_TAG are required parameters')
                    }
                    
                    if (!env.ACCOUNT_NUMBER || env.ACCOUNT_NUMBER == 'null') {
                        error("Account number not found for account name: ${params.ACCOUNT_NAME}. Please add it to the account mapping.")
                    }
                    
                    echo "Account Name: ${params.ACCOUNT_NAME}"
                    echo "Account Number: ${env.ACCOUNT_NUMBER}"
                    echo "Resource Prefix: ${RESOURCE_PREFIX}"
                    echo "Name Prefix: ${env.NAME_PREFIX}"
                    echo "Role Name: ${env.S3_BATCH_INFRA_ROLE_NAME}"
                    echo "Attempting to assume role: ${env.S3_BATCH_INFRA_ROLE_ARN}"
                }
            }
        }
        
        stage('AWS Operations') {
            steps {
                script {
                    withAWS(role: env.S3_BATCH_INFRA_ROLE_ARN, roleSessionName: 'jenkins-s3-batch-copy', region: params.REGION) {
                        echo "Successfully assumed role. Testing AWS credentials..."
                        sh "aws sts get-caller-identity"
                        
                        stage('Auto-detect Source and Destination Buckets') {
                            def sourceBucket = null
                            def destBucket = null
                            
                            retry(3) {
                                def bucketsJson = sh(
                                    script: "aws s3api list-buckets --output json --region ${params.REGION}",
                                    returnStdout: true
                                ).trim()
                                
                                def buckets = sh(
                                    script: "echo '${bucketsJson}' | jq -r '.Buckets[].Name'",
                                    returnStdout: true
                                ).trim().split('\n')
                                
                                for (bucket in buckets) {
                                    try {
                                        def tagsJson = sh(
                                            script: "aws s3api get-bucket-tagging --bucket ${bucket} --region ${params.REGION} --output json 2>&1",
                                            returnStdout: true
                                        ).trim()
                                        
                                        if (tagsJson && !tagsJson.contains('NoSuchTagSet')) {
                                            def operationValue = sh(
                                                script: "echo '${tagsJson}' | jq -r '.TagSet[] | select(.Key==\"operation\") | .Value'",
                                                returnStdout: true
                                            ).trim()
                                            
                                            def envValue = sh(
                                                script: "echo '${tagsJson}' | jq -r '.TagSet[] | select(.Key==\"env\") | .Value'",
                                                returnStdout: true
                                            ).trim()
                                            
                                            def targetValue = sh(
                                                script: "echo '${tagsJson}' | jq -r '.TagSet[] | select(.Key==\"Target\") | .Value'",
                                                returnStdout: true
                                            ).trim()
                                            
                                            if (operationValue == env.OPERATION_TAG && envValue == params.ENV_TAG) {
                                                if (targetValue == 'Source') {
                                                    sourceBucket = bucket
                                                } else if (targetValue == 'Destination') {
                                                    destBucket = bucket
                                                }
                                            }
                                        }
                                    } catch (Exception e) {
                                        continue
                                    }
                                }
                            }
                            
                            if (!sourceBucket) {
                                error("Source bucket not found. Ensure a bucket exists with tags: operation=${env.OPERATION_TAG}, env=${params.ENV_TAG}, Target=Source")
                            }
                            if (!destBucket) {
                                error("Destination bucket not found. Ensure a bucket exists with tags: operation=${env.OPERATION_TAG}, env=${params.ENV_TAG}, Target=Destination")
                            }
                            
                            env.SOURCE_BUCKET = sourceBucket
                            env.DEST_BUCKET = destBucket
                            echo "Source Bucket: ${sourceBucket}"
                            echo "Destination Bucket: ${destBucket}"
                        }
                        
                        stage('Create Report Bucket') {
                            echo "Creating report bucket: ${env.REPORT_BUCKET}"
                            def bucketExists = sh(
                                script: "aws s3api head-bucket --bucket ${env.REPORT_BUCKET} --region ${params.REGION} 2>&1",
                                returnStatus: true
                            )
                            
                            if (bucketExists != 0) {
                                echo "Bucket does not exist. Creating..."
                                def createCmd = params.REGION == 'us-east-1' ? 
                                    "aws s3api create-bucket --bucket ${env.REPORT_BUCKET} --region ${params.REGION}" :
                                    "aws s3api create-bucket --bucket ${env.REPORT_BUCKET} --region ${params.REGION} --create-bucket-configuration LocationConstraint=${params.REGION}"
                                
                                def createExitCode = sh(
                                    script: "${createCmd} 2>&1",
                                    returnStatus: true
                                )
                                
                                if (createExitCode != 0) {
                                    def errorOutput = sh(
                                        script: "${createCmd} 2>&1",
                                        returnStdout: true
                                    )
                                    error("Failed to create report bucket: ${env.REPORT_BUCKET}. Error: ${errorOutput}")
                                }
                                echo "Bucket created successfully"
                            } else {
                                echo "Report bucket already exists: ${env.REPORT_BUCKET}"
                            }
                            
                            // Set lifecycle configuration (whether bucket was just created or already existed)
                            echo "Setting lifecycle configuration..."
                            def lifecycleConfig = '''{
  "Rules": [
    {
      "ID": "DeleteAfter7Days",
      "Status": "Enabled",
      "Filter": {},
      "Expiration": {
        "Days": 7
      }
    }
  ]
}'''
                            writeFile file: 'report-lifecycle.json', text: lifecycleConfig
                            def workspacePath = sh(script: 'pwd', returnStdout: true).trim()
                            sh "cat report-lifecycle.json"
                            retry(3) {
                                sh """
                                    aws s3api put-bucket-lifecycle-configuration \
                                        --bucket ${env.REPORT_BUCKET} \
                                        --lifecycle-configuration file://${workspacePath}/report-lifecycle.json \
                                        --region ${params.REGION}
                                """
                            }
                            echo "Report bucket lifecycle configuration set successfully"
                        }
                        
                        stage('Create Manifest Bucket') {
                            echo "Creating manifest bucket: ${env.MANIFEST_BUCKET}"
                            def bucketExists = sh(
                                script: "aws s3api head-bucket --bucket ${env.MANIFEST_BUCKET} --region ${params.REGION} 2>&1",
                                returnStatus: true
                            )
                            
                            if (bucketExists != 0) {
                                echo "Bucket does not exist. Creating..."
                                def createCmd = params.REGION == 'us-east-1' ? 
                                    "aws s3api create-bucket --bucket ${env.MANIFEST_BUCKET} --region ${params.REGION}" :
                                    "aws s3api create-bucket --bucket ${env.MANIFEST_BUCKET} --region ${params.REGION} --create-bucket-configuration LocationConstraint=${params.REGION}"
                                
                                def createExitCode = sh(
                                    script: "${createCmd} 2>&1",
                                    returnStatus: true
                                )
                                
                                if (createExitCode != 0) {
                                    def errorOutput = sh(
                                        script: "${createCmd} 2>&1",
                                        returnStdout: true
                                    )
                                    error("Failed to create manifest bucket: ${env.MANIFEST_BUCKET}. Error: ${errorOutput}")
                                }
                                echo "Bucket created successfully"
                            } else {
                                echo "Manifest bucket already exists: ${env.MANIFEST_BUCKET}"
                            }
                            
                            // Set lifecycle configuration (whether bucket was just created or already existed)
                            echo "Setting lifecycle configuration..."
                            def lifecycleConfig = '''{
  "Rules": [
    {
      "ID": "DeleteAfter7Days",
      "Status": "Enabled",
      "Filter": {},
      "Expiration": {
        "Days": 7
      }
    }
  ]
}'''
                            writeFile file: 'manifest-lifecycle.json', text: lifecycleConfig
                            def workspacePath = sh(script: 'pwd', returnStdout: true).trim()
                            sh "cat manifest-lifecycle.json"
                            retry(3) {
                                sh """
                                    aws s3api put-bucket-lifecycle-configuration \
                                        --bucket ${env.MANIFEST_BUCKET} \
                                        --lifecycle-configuration file://${workspacePath}/manifest-lifecycle.json \
                                        --region ${params.REGION}
                                """
                            }
                            echo "Manifest bucket lifecycle configuration set successfully"
                        }
                        
                        stage('Create Batch Job Role') {
                            def trustPolicy = '{"Version":"2012-10-17","Statement":[{"Effect":"Allow","Principal":{"Service":"batchoperations.s3.amazonaws.com"},"Action":"sts:AssumeRole"}]}'
                            
                            retry(3) {
                                def roleExists = sh(
                                    script: "aws iam get-role --role-name ${env.BATCH_JOB_ROLE_NAME} 2>&1",
                                    returnStatus: true
                                )
                                
                                if (roleExists != 0) {
                                    sh """
                                        aws iam create-role \
                                            --role-name ${env.BATCH_JOB_ROLE_NAME} \
                                            --assume-role-policy-document '${trustPolicy}'
                                    """
                                    
                                    sleep(time: 10, unit: 'SECONDS')
                                }
                            }
                            
                            // Build execution policy using Groovy map (ensures valid JSON)
                            def executionPolicy = [
                                Version: "2012-10-17",
                                Statement: [
                                    [
                                        Effect: "Allow",
                                        Action: [
                                            "s3:GetObject",
                                            "s3:GetObjectVersion",
                                            "s3:GetObjectTagging",
                                            "s3:GetObjectVersionTagging",
                                            "s3:ListBucket"
                                        ],
                                        Resource: [
                                            "arn:aws:s3:::${env.SOURCE_BUCKET}",
                                            "arn:aws:s3:::${env.SOURCE_BUCKET}/*"
                                        ]
                                    ],
                                    [
                                        Effect: "Allow",
                                        Action: [
                                            "s3:PutObject",
                                            "s3:PutObjectTagging",
                                            "s3:GetObject",
                                            "s3:GetObjectVersion",
                                            "s3:ListBucket"
                                        ],
                                        Resource: [
                                            "arn:aws:s3:::${env.DEST_BUCKET}",
                                            "arn:aws:s3:::${env.DEST_BUCKET}/*"
                                        ]
                                    ],
                                    [
                                        Effect: "Allow",
                                        Action: [
                                            "s3:GetObject",
                                            "s3:GetObjectVersion",
                                            "s3:ListBucket"
                                        ],
                                        Resource: [
                                            "arn:aws:s3:::${env.MANIFEST_BUCKET}",
                                            "arn:aws:s3:::${env.MANIFEST_BUCKET}/*"
                                        ]
                                    ],
                                    [
                                        Effect: "Allow",
                                        Action: [
                                            "s3:PutObject",
                                            "s3:ListBucket"
                                        ],
                                        Resource: [
                                            "arn:aws:s3:::${env.REPORT_BUCKET}",
                                            "arn:aws:s3:::${env.REPORT_BUCKET}/*",
                                            "arn:aws:s3:::${env.MANIFEST_BUCKET}/*"
                                        ]
                                    ],
                                    [
                                        Effect: "Allow",
                                        Action: [
                                            "s3:PutInventoryConfiguration"
                                        ],
                                        Resource: "arn:aws:s3:::${env.DEST_BUCKET}"
                                    ]
                                ]
                            ]
                            
                            // Convert to JSON and write to file
                            def workspacePath = sh(script: 'pwd', returnStdout: true).trim()
                            def jsonPolicy = groovy.json.JsonOutput.toJson(executionPolicy)
                            writeFile file: 'batch-job-execution-policy.json', text: groovy.json.JsonOutput.prettyPrint(jsonPolicy)
                            
                            // Validate and show JSON
                            sh """
                                echo "=== Policy JSON ==="
                                cat batch-job-execution-policy.json
                                echo "=== Validating JSON ==="
                                python3 -m json.tool batch-job-execution-policy.json > /dev/null && echo "JSON is valid" || (echo "Invalid JSON!" && exit 1)
                            """
                            
                            echo "Attaching execution policy to role: ${env.BATCH_JOB_ROLE_NAME}"
                            retry(3) {
                                sh """
                                    aws iam put-role-policy \
                                        --role-name ${env.BATCH_JOB_ROLE_NAME} \
                                        --policy-name BatchJobExecutionPolicy \
                                        --policy-document file://${workspacePath}/batch-job-execution-policy.json
                                """
                            }
                            echo "Execution policy attached successfully."
                        }
                        
                        stage('Create S3 Batch Job') {
                            def role3Arn = "arn:aws:iam::${env.ACCOUNT_NUMBER}:role/${env.BATCH_JOB_ROLE_NAME}"
                            def filterJson = params.SOURCE_PREFIX ? 
                                """{"KeyNameConstraint":{"MatchAnyPrefix":["${params.SOURCE_PREFIX}"]}}""" : 
                                '{}'
                            def destPath = params.DEST_PREFIX ? "${params.DEST_PREFIX}/" : ""
                            
                            // Build JSON using Groovy maps for proper formatting
                            def operationMap = [
                                S3CopyObject: [
                                    TargetResource: "arn:aws:s3:::${env.DEST_BUCKET}/${destPath}",
                                    CannedAccessControlList: "private",
                                    MetadataDirective: "COPY",
                                    TaggingDirective: "COPY"
                                ]
                            ]
                            
                            def manifestGeneratorMap = [
                                S3JobManifestGenerator: [
                                    ExpectedBucketOwner: env.ACCOUNT_NUMBER,
                                    SourceBucket: "arn:aws:s3:::${env.SOURCE_BUCKET}",
                                    EnableManifestOutput: true,
                                    ManifestOutputLocation: [
                                        ExpectedManifestBucketOwner: env.ACCOUNT_NUMBER,
                                        Bucket: "arn:aws:s3:::${env.MANIFEST_BUCKET}",
                                        ManifestPrefix: "manifests/",
                                        ManifestFormat: "S3InventoryReport_CSV_20211130"
                                    ]
                                ]
                            ]
                            
                            // Add Filter only if SOURCE_PREFIX is provided
                            if (params.SOURCE_PREFIX) {
                                manifestGeneratorMap.S3JobManifestGenerator.Filter = [
                                    KeyNameConstraint: [
                                        MatchAnyPrefix: [params.SOURCE_PREFIX]
                                    ]
                                ]
                            }
                            
                            def reportMap = [
                                Bucket: "arn:aws:s3:::${env.REPORT_BUCKET}",
                                Prefix: "reports/",
                                Format: "Report_CSV_20180820",
                                Enabled: true,
                                ReportScope: "AllTasks"
                            ]
                            
                            // Convert to JSON strings
                            def operationJson = groovy.json.JsonOutput.toJson(operationMap)
                            def manifestGeneratorJson = groovy.json.JsonOutput.toJson(manifestGeneratorMap)
                            def reportJson = groovy.json.JsonOutput.toJson(reportMap)
                            
                            // Show the JSON for debugging
                            echo "=== Operation JSON ==="
                            echo operationJson
                            echo "=== Manifest Generator JSON ==="
                            echo manifestGeneratorJson
                            echo "=== Report JSON ==="
                            echo reportJson
                            
                            def jobOutput = ''
                            retry(3) {
                                jobOutput = sh(
                                    script: """
                                        aws s3control create-job \
                                            --account-id ${env.ACCOUNT_NUMBER} \
                                            --operation '${operationJson}' \
                                            --manifest-generator '${manifestGeneratorJson}' \
                                            --manifest '{}' \
                                            --report '${reportJson}' \
                                            --priority ${params.PRIORITY} \
                                            --role-arn ${role3Arn} \
                                            --region ${params.REGION} \
                                            --no-confirmation-required \
                                            --output json
                                    """,
                                    returnStdout: true
                                ).trim()
                            }
                            
                            def jobId = sh(
                                script: "echo '${jobOutput}' | jq -r '.JobId'",
                                returnStdout: true
                            ).trim()
                            
                            if (!jobId || jobId == 'null') {
                                error("Failed to create batch job. Response: ${jobOutput}")
                            }
                            
                            env.S3_BATCH_JOB_ID = jobId
                            echo "S3 Batch Job created successfully. Job ID: ${jobId}"
                        }
                    }
                }
            }
        }
    }
    
    post {
        always {
            script {
                if (env.S3_BATCH_JOB_ID) {
                    echo "Job ID: ${env.S3_BATCH_JOB_ID}"
                    echo "Monitor: aws s3control describe-job --account-id ${env.ACCOUNT_NUMBER} --job-id ${env.S3_BATCH_JOB_ID} --region ${params.REGION}"
                }
            }
        }
        failure {
            echo "Pipeline failed. Check logs for details."
        }
    }
}
