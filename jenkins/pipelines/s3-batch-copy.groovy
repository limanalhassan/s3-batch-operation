pipeline {
    agent any
    
    options {
        buildDiscarder(logRotator(numToKeepStr: '10'))
        timestamps()
    }
    
    parameters {
        string(name: 'ACCOUNT_NUMBER', defaultValue: '', description: 'AWS Account Number')
        string(name: 'ACCOUNT_NAME', defaultValue: '', description: 'Account Name')
        string(name: 'S3_BATCH_INFRA_ROLE_ARN', defaultValue: '', description: 'ARN of Role 2 (S3 Batch Infrastructure Role)')
        string(name: 'ENV_TAG', defaultValue: '', description: 'Environment tag value to filter buckets (e.g., dev, staging, prod)')
        string(name: 'SOURCE_PREFIX', defaultValue: '', description: 'Bucket prefix to copy from')
        string(name: 'DEST_PREFIX', defaultValue: '', description: 'Bucket prefix to copy to')
        string(name: 'REGION', defaultValue: 'us-east-1', description: 'AWS Region')
        string(name: 'PRIORITY', defaultValue: '2', description: 'Job priority (higher number = higher priority)')
    }
    
    environment {
        OPERATION_TAG = 's3BatchOperations'
        REPORT_BUCKET = "${params.ACCOUNT_NAME}-report-${params.ACCOUNT_NAME}"
        MANIFEST_BUCKET = "${params.ACCOUNT_NAME}-manifest-${params.ACCOUNT_NAME}"
        BATCH_JOB_ROLE_NAME = "${params.ACCOUNT_NAME}-batch-job-role"
    }
    
    
    stages {
        stage('Validate Parameters') {
            steps {
                script {
                    if (!params.ACCOUNT_NUMBER || !params.ACCOUNT_NAME || !params.S3_BATCH_INFRA_ROLE_ARN || !params.ENV_TAG) {
                        error('ACCOUNT_NUMBER, ACCOUNT_NAME, S3_BATCH_INFRA_ROLE_ARN, and ENV_TAG are required parameters')
                    }
                }
            }
        }
        
        stage('Auto-detect Source and Destination Buckets') {
            steps {
                script {
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
                }
            }
        }
        
        stage('Create Report Bucket') {
            steps {
                script {
                    withAWS(role: params.S3_BATCH_INFRA_ROLE_ARN, roleSessionName: 'jenkins-s3-batch-copy') {
                        retry(3) {
                            def bucketExists = sh(
                                script: "aws s3api head-bucket --bucket ${env.REPORT_BUCKET} --region ${params.REGION} 2>&1",
                                returnStatus: true
                            )
                            
                            if (bucketExists != 0) {
                                def createCmd = params.REGION == 'us-east-1' ? 
                                    "aws s3api create-bucket --bucket ${env.REPORT_BUCKET} --region ${params.REGION}" :
                                    "aws s3api create-bucket --bucket ${env.REPORT_BUCKET} --region ${params.REGION} --create-bucket-configuration LocationConstraint=${params.REGION}"
                                
                                sh """
                                    ${createCmd}
                                    aws s3api put-bucket-lifecycle-configuration \
                                        --bucket ${env.REPORT_BUCKET} \
                                        --lifecycle-configuration '{"Rules":[{"Id":"DeleteAfter7Days","Status":"Enabled","Expiration":{"Days":7}}]}' \
                                        --region ${params.REGION}
                                """
                            }
                        }
                    }
                }
            }
        }
        
        stage('Create Manifest Bucket') {
            steps {
                script {
                    withAWS(role: params.S3_BATCH_INFRA_ROLE_ARN, roleSessionName: 'jenkins-s3-batch-copy') {
                        retry(3) {
                            def bucketExists = sh(
                                script: "aws s3api head-bucket --bucket ${env.MANIFEST_BUCKET} --region ${params.REGION} 2>&1",
                                returnStatus: true
                            )
                            
                            if (bucketExists != 0) {
                                def createCmd = params.REGION == 'us-east-1' ? 
                                    "aws s3api create-bucket --bucket ${env.MANIFEST_BUCKET} --region ${params.REGION}" :
                                    "aws s3api create-bucket --bucket ${env.MANIFEST_BUCKET} --region ${params.REGION} --create-bucket-configuration LocationConstraint=${params.REGION}"
                                
                                sh """
                                    ${createCmd}
                                    aws s3api put-bucket-lifecycle-configuration \
                                        --bucket ${env.MANIFEST_BUCKET} \
                                        --lifecycle-configuration '{"Rules":[{"Id":"DeleteAfter7Days","Status":"Enabled","Expiration":{"Days":7}}]}' \
                                        --region ${params.REGION}
                                """
                            }
                        }
                    }
                }
            }
        }
        
        stage('Create Batch Job Role') {
            steps {
                script {
                    withAWS(role: params.S3_BATCH_INFRA_ROLE_ARN, roleSessionName: 'jenkins-s3-batch-copy') {
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
                        
                        def executionPolicy = """
                        {
                            "Version": "2012-10-17",
                            "Statement": [
                                {
                                    "Effect": "Allow",
                                    "Action": [
                                        "s3:GetObject",
                                        "s3:GetObjectVersion",
                                        "s3:GetObjectTagging",
                                        "s3:GetObjectVersionTagging",
                                        "s3:ListBucket"
                                    ],
                                    "Resource": [
                                        "arn:aws:s3:::${env.SOURCE_BUCKET}",
                                        "arn:aws:s3:::${env.SOURCE_BUCKET}/*"
                                    ]
                                },
                                {
                                    "Effect": "Allow",
                                    "Action": [
                                        "s3:PutObject",
                                        "s3:PutObjectTagging",
                                        "s3:GetObject",
                                        "s3:GetObjectVersion",
                                        "s3:ListBucket"
                                    ],
                                    "Resource": [
                                        "arn:aws:s3:::${env.DEST_BUCKET}",
                                        "arn:aws:s3:::${env.DEST_BUCKET}/*"
                                    ]
                                },
                                {
                                    "Effect": "Allow",
                                    "Action": [
                                        "s3:GetObject",
                                        "s3:GetObjectVersion",
                                        "s3:ListBucket"
                                    ],
                                    "Resource": [
                                        "arn:aws:s3:::${env.MANIFEST_BUCKET}",
                                        "arn:aws:s3:::${env.MANIFEST_BUCKET}/*"
                                    ]
                                },
                                {
                                    "Effect": "Allow",
                                    "Action": [
                                        "s3:PutObject",
                                        "s3:ListBucket"
                                    ],
                                    "Resource": [
                                        "arn:aws:s3:::${env.REPORT_BUCKET}",
                                        "arn:aws:s3:::${env.REPORT_BUCKET}/*",
                                        "arn:aws:s3:::${env.MANIFEST_BUCKET}/*"
                                    ]
                                },
                                {
                                    "Effect": "Allow",
                                    "Action": [
                                        "s3:PutInventoryConfiguration"
                                    ],
                                    "Resource": "arn:aws:s3:::${env.DEST_BUCKET}"
                                }
                            ]
                        }
                        """
                        
                        writeFile file: '/tmp/batch-job-execution-policy.json', text: executionPolicy
                        
                        retry(3) {
                            sh """
                                aws iam put-role-policy \
                                    --role-name ${env.BATCH_JOB_ROLE_NAME} \
                                    --policy-name BatchJobExecutionPolicy \
                                    --policy-document file:///tmp/batch-job-execution-policy.json
                            """
                        }
                    }
                }
            }
        }
        
        stage('Create S3 Batch Job') {
            steps {
                script {
                    withAWS(role: params.S3_BATCH_INFRA_ROLE_ARN, roleSessionName: 'jenkins-s3-batch-copy') {
                        def role3Arn = "arn:aws:iam::${params.ACCOUNT_NUMBER}:role/${env.BATCH_JOB_ROLE_NAME}"
                        def filterJson = params.SOURCE_PREFIX ? 
                            """{"KeyNameConstraint":{"MatchAnyPrefix":["${params.SOURCE_PREFIX}"]}}""" : 
                            '{}'
                        def destPath = params.DEST_PREFIX ? "${params.DEST_PREFIX}/" : ""
                        
                        def operationJson = """{"S3CopyObject":{"TargetResource":"arn:aws:s3:::${env.DEST_BUCKET}/${destPath}","CannedAccessControlList":"private","MetadataDirective":"COPY","TaggingDirective":"COPY"}}"""
                        def manifestGeneratorJson = """{"S3JobManifestGenerator":{"ExpectedBucketOwner":"${params.ACCOUNT_NUMBER}","SourceBucket":"arn:aws:s3:::${env.SOURCE_BUCKET}","EnableManifestOutput":true,"ManifestOutputLocation":{"ExpectedManifestBucketOwner":"${params.ACCOUNT_NUMBER}","Bucket":"arn:aws:s3:::${env.MANIFEST_BUCKET}","ManifestPrefix":"manifests/","ManifestFormat":"S3InventoryReport_CSV_20211130"},"Filter":${filterJson}}}"""
                        def reportJson = """{"Bucket":"arn:aws:s3:::${env.REPORT_BUCKET}","Prefix":"reports/","Format":"Report_CSV_20180820","Enabled":true,"ReportScope":"AllTasks"}"""
                        
                        writeFile file: '/tmp/operation.json', text: operationJson
                        writeFile file: '/tmp/report.json', text: reportJson
                        writeFile file: '/tmp/manifest-generator.json', text: manifestGeneratorJson
                        
                        def jobOutput = ''
                        retry(3) {
                            jobOutput = sh(
                                script: """
                                    aws s3control create-job \
                                        --account-id ${params.ACCOUNT_NUMBER} \
                                        --operation file:///tmp/operation.json \
                                        --report file:///tmp/report.json \
                                        --manifest-generator file:///tmp/manifest-generator.json \
                                        --priority ${params.PRIORITY} \
                                        --role-arn ${role3Arn} \
                                        --region ${params.REGION} \
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
    
    post {
        always {
            script {
                if (env.S3_BATCH_JOB_ID) {
                    echo "Job ID: ${env.S3_BATCH_JOB_ID}"
                    echo "Monitor: aws s3control describe-job --account-id ${params.ACCOUNT_NUMBER} --job-id ${env.S3_BATCH_JOB_ID} --region ${params.REGION}"
                }
            }
        }
        failure {
            echo "Pipeline failed. Check logs for details."
        }
    }
}
