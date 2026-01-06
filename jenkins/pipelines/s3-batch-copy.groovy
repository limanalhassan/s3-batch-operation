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
                            
                            // Validate JSON
                            sh "python3 -m json.tool batch-job-execution-policy.json > /dev/null || (echo 'Invalid JSON!' && exit 1)"
                            
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
                            // Check AWS CLI version (manifest-generator requires AWS CLI v2.0.0+)
                            echo "Checking AWS CLI version..."
                            def awsCliVersion = sh(
                                script: "aws --version 2>&1",
                                returnStdout: true
                            ).trim()
                            echo "AWS CLI Version: ${awsCliVersion}"
                            
                            // Check if AWS CLI v2 is installed (v2 has 'aws-cli/2' in version string)
                            def isCliV2 = awsCliVersion.contains('aws-cli/2')
                            def homeDir = sh(script: 'echo $HOME', returnStdout: true).trim()
                            def awsCmd = 'aws'  // Default to system aws
                            
                            if (!isCliV2) {
                                echo "AWS CLI v1 detected. Installing AWS CLI v2 to user directory..."
                                
                                // Install AWS CLI v2 to user's home directory (no sudo required)
                                def awsCliV2Path = "${homeDir}/.local/aws-cli"
                                
                                sh """
                                    cd /tmp
                                    rm -rf aws awscliv2.zip
                                    curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o "awscliv2.zip" || exit 1
                                    unzip -o -q awscliv2.zip || exit 1
                                    ./aws/install -i ${awsCliV2Path} -b ${homeDir}/.local/bin --update || exit 1
                                    rm -f awscliv2.zip
                                    rm -rf aws
                                """
                                
                                // Update PATH for this session to use AWS CLI v2
                                env.PATH = "${homeDir}/.local/bin:${env.PATH}"
                                
                                // Verify installation
                                def newVersion = sh(
                                    script: "${homeDir}/.local/bin/aws --version 2>&1",
                                    returnStdout: true
                                ).trim()
                                echo "New AWS CLI Version: ${newVersion}"
                                
                                if (!newVersion.contains('aws-cli/2')) {
                                    error("Failed to install AWS CLI v2. Current version: ${newVersion}")
                                }
                                
                                // Use the newly installed AWS CLI v2
                                awsCmd = "${homeDir}/.local/bin/aws"
                                echo "Successfully installed AWS CLI v2 to ${homeDir}/.local/bin"
                            } else {
                                echo "AWS CLI v2 is already installed"
                            }
                            
                            def role3Arn = "arn:aws:iam::${env.ACCOUNT_NUMBER}:role/${env.BATCH_JOB_ROLE_NAME}"
                            
                            // Build JSON using Groovy maps for proper formatting
                            // Note: TargetResource must be bucket ARN only (no path/prefix)
                            // Use TargetKeyPrefix to add a prefix to destination objects
                            def operationMap = [
                                S3PutObjectCopy: [
                                    TargetResource: "arn:aws:s3:::${env.DEST_BUCKET}",
                                    CannedAccessControlList: "private",
                                    MetadataDirective: "COPY"
                                ]
                            ]
                            
                            // Add TargetKeyPrefix if DEST_PREFIX is provided
                            if (params.DEST_PREFIX && params.DEST_PREFIX.trim()) {
                                operationMap.S3PutObjectCopy.TargetKeyPrefix = params.DEST_PREFIX.trim()
                                echo "Objects will be copied to destination prefix: ${params.DEST_PREFIX}"
                            }
                            
                            def reportMap = [
                                Bucket: "arn:aws:s3:::${env.REPORT_BUCKET}",  // Bucket ARN (required by AWS)
                                Prefix: "reports/",
                                Format: "Report_CSV_20180820",
                                Enabled: true,
                                ReportScope: "AllTasks"
                            ]
                            
                            def workspacePath = sh(script: 'pwd', returnStdout: true).trim()
                            
                            // Generate manifest file from source bucket objects
                            echo "Generating manifest file from source bucket: ${env.SOURCE_BUCKET}"
                            def manifestFileName = "manifest-${System.currentTimeMillis()}.csv"
                            def manifestLocalPath = "${workspacePath}/${manifestFileName}"
                            
                            // Create manifest CSV file (format: Bucket,Key)
                            // AWS S3 Batch Operations does NOT want a header row
                            // Start with empty file
                            sh """
                                touch ${manifestLocalPath}
                            """
                            
                            // List objects and append to manifest
                            // Properly quote CSV fields to handle commas in keys
                            def listPrefix = params.SOURCE_PREFIX ?: ""
                            echo "Listing objects with prefix: '${listPrefix}'"
                            
                            // List ALL objects with pagination to avoid duplicates
                            // Write directly to manifest CSV to avoid memory issues with large buckets
                            def listJsonFile = "${workspacePath}/list-objects-${System.currentTimeMillis()}.json"
                            
                            // Initialize manifest file (empty)
                            sh """
                                > ${manifestLocalPath}
                            """
                            
                            def continuationToken = ""
                            def lastKey = null  // For --start-after fallback when token is missing
                            def previousLastKey = null  // Track previous lastKey to detect loops
                            def pageCount = 0
                            def totalObjects = 0
                            def maxPages = 1000  // Safety limit to prevent infinite loops (1M objects max)
                            
                            // Paginate through all objects
                            while (pageCount < maxPages) {
                                pageCount++
                                def listCmd = "${awsCmd} s3api list-objects-v2 --bucket ${env.SOURCE_BUCKET} --region ${params.REGION} --max-items 1000 --output json"
                                
                                // Only add --prefix if it's not empty
                                if (listPrefix && listPrefix.trim()) {
                                    listCmd += " --prefix \"${listPrefix}\""
                                }
                                
                                // Use continuation token if available (preferred method)
                                if (continuationToken) {
                                    listCmd += " --continuation-token '${continuationToken}'"
                                    lastKey = null  // Clear lastKey when using token
                                } else if (lastKey) {
                                    // Fallback: use --start-after with last key from previous page
                                    listCmd += " --start-after \"${lastKey}\""
                                    echo "Using --start-after with last key from previous page: ${lastKey}"
                                }
                                
                                sh """
                                    ${listCmd} > ${listJsonFile} 2>&1 || {
                                        echo "Failed to list objects (page ${pageCount})"
                                        cat ${listJsonFile}
                                        exit 1
                                    }
                                """
                                
                                // Check for errors
                                def listOutput = readFile(listJsonFile)
                                if (listOutput.contains('An error occurred') || listOutput.contains('AccessDenied') || listOutput.contains('NoSuchBucket')) {
                                    error("Failed to list objects from bucket ${env.SOURCE_BUCKET}. Output: ${listOutput}")
                                }
                                
                                // Debug: Show actual response structure
                                def responseDebug = sh(
                                    script: "jq '{IsTruncated, KeyCount, MaxKeys, NextContinuationToken: (.NextContinuationToken != null), ContentsCount: (.Contents | length)}' ${listJsonFile}",
                                    returnStdout: true
                                ).trim()
                                echo "Page ${pageCount} Response Debug: ${responseDebug}"
                                
                                // Check if IsTruncated field exists and its actual value
                                def hasIsTruncated = sh(
                                    script: "jq 'has(\"IsTruncated\")' ${listJsonFile}",
                                    returnStdout: true
                                ).trim()
                                echo "Page ${pageCount} Has IsTruncated field: ${hasIsTruncated}"
                                
                                // Get actual IsTruncated value (handle boolean properly)
                                def actualIsTruncated = sh(
                                    script: "jq -r 'if .IsTruncated == true then \"true\" elif .IsTruncated == false then \"false\" else \"missing\" end' ${listJsonFile}",
                                    returnStdout: true
                                ).trim()
                                echo "Page ${pageCount} Actual IsTruncated value: ${actualIsTruncated}"
                                
                                // Show top-level keys to understand response structure
                                def topKeys = sh(
                                    script: "jq 'keys | .[]' ${listJsonFile} | head -10",
                                    returnStdout: true
                                ).trim()
                                echo "Page ${pageCount} Top-level keys: ${topKeys}"
                                
                                // If we got exactly 1000 objects and IsTruncated is missing/false, 
                                // we should still try to get next page using the last key as marker
                                // But list-objects-v2 uses continuation tokens, not markers
                                // So if there's no token, we can't continue
                                
                                // Merge Contents into combined file
                                def pageObjectsStr = sh(
                                    script: "jq -r '.Contents // [] | length' ${listJsonFile}",
                                    returnStdout: true
                                ).trim()
                                
                                def pageObjects = 0
                                if (pageObjectsStr && pageObjectsStr != 'null' && pageObjectsStr != '') {
                                    try {
                                        pageObjects = pageObjectsStr.toInteger()
                                    } catch (NumberFormatException e) {
                                        echo "WARNING: Could not parse page object count '${pageObjectsStr}', defaulting to 0"
                                        pageObjects = 0
                                    }
                                }
                                
                                if (pageObjects > 0) {
                                    // Write objects directly to manifest CSV (memory-efficient)
                                    sh """
                                        jq -r --arg bucket '${env.SOURCE_BUCKET}' '.Contents[]? | "\\"" + \$bucket + "\\",\\"" + (.Key | gsub("\\""; "\\"\\"")) + "\\""' ${listJsonFile} >> ${manifestLocalPath}
                                    """
                                    totalObjects += pageObjects
                                    echo "Page ${pageCount}: Found ${pageObjects} objects (total so far: ${totalObjects})"
                                } else if (lastKey && !continuationToken) {
                                    // If using --start-after and got 0 objects, we've reached the end
                                    echo "Got 0 objects when using --start-after. Reached end of bucket."
                                    break
                                }
                                
                                // Check if there are more results (IsTruncated field)
                                def isTruncatedStr = sh(
                                    script: "jq -r '.IsTruncated // false' ${listJsonFile}",
                                    returnStdout: true
                                ).trim()
                                
                                def isTruncated = (isTruncatedStr == 'true' || isTruncatedStr == 'True')
                                echo "Page ${pageCount}: IsTruncated = ${isTruncatedStr} (parsed as: ${isTruncated})"
                                
                                // Get continuation token if available
                                def continuationTokenStr = sh(
                                    script: "jq -r '.NextContinuationToken // empty' ${listJsonFile}",
                                    returnStdout: true
                                ).trim()
                                
                                continuationToken = (continuationTokenStr && continuationTokenStr != 'null' && continuationTokenStr != '') ? continuationTokenStr : ""
                                
                                if (continuationToken) {
                                    echo "Page ${pageCount}: NextContinuationToken found (length: ${continuationToken.length()})"
                                } else {
                                    echo "Page ${pageCount}: No NextContinuationToken"
                                }
                                
                                // Special case: If we got exactly 1000 objects (max page size),
                                // there might be more objects even if IsTruncated is false
                                // Get the last key to use with --start-after for next page
                                if (pageObjects == 1000) {
                                    def currentLastKey = sh(
                                        script: "jq -r '.Contents[-1].Key // empty' ${listJsonFile}",
                                        returnStdout: true
                                    ).trim()
                                    
                                    if (currentLastKey && currentLastKey != 'null' && currentLastKey != '') {
                                        // Loop detection: if we're using --start-after and got the same lastKey, we're stuck
                                        if (lastKey && !continuationToken && currentLastKey == lastKey) {
                                            echo "Loop detected: same lastKey returned when using --start-after (${lastKey}). Breaking pagination."
                                            break
                                        }
                                        
                                        previousLastKey = lastKey
                                        lastKey = currentLastKey
                                        echo "Page ${pageCount}: Last key saved for potential --start-after: ${lastKey}"
                                        
                                        // If IsTruncated is false but we got max objects, force continuation
                                        // But only if we haven't seen this key before (loop prevention)
                                        if (!isTruncated && !continuationToken) {
                                            if (lastKey == previousLastKey) {
                                                echo "Got exactly 1000 objects but IsTruncated=false and same lastKey. Finished pagination."
                                                break
                                            } else {
                                                echo "Got exactly 1000 objects but IsTruncated=false. Will try --start-after in next iteration."
                                                isTruncated = true  // Force continuation to try next page
                                            }
                                        }
                                    }
                                } else {
                                    // If we got fewer than 1000 objects, clear lastKey and we're done
                                    if (pageObjects < 1000) {
                                        echo "Got ${pageObjects} objects (< 1000). Finished pagination."
                                        lastKey = null
                                    }
                                }
                                
                                // Break if not truncated (no more results) and no token
                                if (!isTruncated && !continuationToken && !lastKey) {
                                    echo "IsTruncated: false and no continuation token. Finished pagination after ${pageCount} pages."
                                    break
                                }
                                
                                // If truncated but no token, use --start-after with last key as fallback
                                if (isTruncated && !continuationToken) {
                                    if (lastKey) {
                                        echo "IsTruncated: true but no NextContinuationToken. Will use --start-after with last key: ${lastKey}"
                                        // Continue with lastKey - it will be used in next iteration
                                    } else {
                                        // Try to get last key from current page
                                        def currentLastKey = sh(
                                            script: "jq -r '.Contents[-1].Key // empty' ${listJsonFile}",
                                            returnStdout: true
                                        ).trim()
                                        
                                        if (currentLastKey && currentLastKey != 'null' && currentLastKey != '') {
                                            lastKey = currentLastKey
                                            echo "Retrieved last key for --start-after: ${lastKey}"
                                        } else {
                                            error("ERROR: IsTruncated is true but no NextContinuationToken found and cannot get last key. Cannot continue pagination.")
                                        }
                                    }
                                }
                                
                                // Continue to next page if we have a way to continue
                                if (isTruncated || continuationToken || lastKey) {
                                    echo "Continuing to next page (IsTruncated: ${isTruncated}, has token: ${continuationToken ? 'yes' : 'no'}, has lastKey: ${lastKey ? 'yes' : 'no'})..."
                                } else {
                                    echo "No way to continue pagination. Breaking."
                                    break
                                }
                            }
                            
                            // Safety check: if we hit max pages, warn but continue
                            if (pageCount >= maxPages) {
                                echo "WARNING: Reached maximum page limit (${maxPages}). Stopping pagination. Some objects may be missing."
                            }
                            
                            // Clean up temporary JSON file (manifest CSV already written)
                            sh "rm -f ${listJsonFile}"
                            
                            // Verify manifest was created correctly
                            def manifestLineCount = sh(
                                script: "wc -l < ${manifestLocalPath} | tr -d ' '",
                                returnStdout: true
                            ).trim().toInteger()
                            
                            echo "Found ${totalObjects} total objects in bucket ${env.SOURCE_BUCKET} with prefix '${listPrefix ?: '(root)'}' (across ${pageCount} pages)"
                            echo "Manifest contains ${manifestLineCount} lines"
                            
                            if (totalObjects == 0) {
                                error("No objects found in source bucket ${env.SOURCE_BUCKET} with prefix '${listPrefix ?: '(root)'}'. Cannot create batch job with empty manifest.")
                            }
                            
                            // Warn if counts don't match, but don't fail (should match if pagination worked correctly)
                            if (manifestLineCount != totalObjects) {
                                echo "WARNING: Manifest line count (${manifestLineCount}) does not exactly match object count (${totalObjects}), but proceeding..."
                            }
                            
                            // Upload manifest to S3
                            def manifestS3Key = "manifests/${manifestFileName}"
                            echo "Uploading manifest to s3://${env.MANIFEST_BUCKET}/${manifestS3Key}"
                            
                            sh """
                                ${awsCmd} s3 cp ${manifestLocalPath} \
                                    s3://${env.MANIFEST_BUCKET}/${manifestS3Key} \
                                    --region ${params.REGION}
                            """
                            
                            // Manifest ARN for batch job
                            def manifestArn = "arn:aws:s3:::${env.MANIFEST_BUCKET}/${manifestS3Key}"
                            echo "Manifest ARN: ${manifestArn}"
                            
                            // Create manifest specification JSON
                            def manifestSpec = [
                                Spec: [
                                    Format: "S3BatchOperations_CSV_20180820",
                                    Fields: ["Bucket", "Key"]
                                ],
                                Location: [
                                    ObjectArn: manifestArn,
                                    ETag: sh(
                                        script: "${awsCmd} s3api head-object --bucket ${env.MANIFEST_BUCKET} --key ${manifestS3Key} --region ${params.REGION} --query ETag --output text | tr -d '\"'",
                                        returnStdout: true
                                    ).trim()
                                ]
                            ]
                            
                            def manifestSpecJson = groovy.json.JsonOutput.toJson(manifestSpec)
                            writeFile file: 'manifest.json', text: manifestSpecJson
                            
                            // Write operation and report JSON files
                            def operationJson = groovy.json.JsonOutput.toJson(operationMap)
                            def reportJson = groovy.json.JsonOutput.toJson(reportMap)
                            
                            writeFile file: 'operation.json', text: operationJson
                            writeFile file: 'report.json', text: reportJson
                            
                            echo "Manifest file contains ${totalObjects} objects"
                            
                            def jobOutput = ''
                            retry(3) {
                                // Run command ONCE and capture both output and exit code
                                echo "Executing create-job command..."
                                
                                // Use a temporary file to capture both stdout and exit code
                                def outputFile = "${workspacePath}/create-job-output-${System.currentTimeMillis()}.json"
                                def exitCodeFile = "${workspacePath}/create-job-exit-${System.currentTimeMillis()}.txt"
                                
                                // Run command and capture exit code without failing the step
                                def shExitCode = sh(
                                    script: """
                                        set +e
                                        ${awsCmd} s3control create-job \\
                                            --account-id ${env.ACCOUNT_NUMBER} \\
                                            --operation file://${workspacePath}/operation.json \\
                                            --manifest file://${workspacePath}/manifest.json \\
                                            --report file://${workspacePath}/report.json \\
                                            --priority ${params.PRIORITY} \\
                                            --role-arn ${role3Arn} \\
                                            --region ${params.REGION} \\
                                            --no-confirmation-required \\
                                            --output json > ${outputFile} 2>&1
                                        echo \$? > ${exitCodeFile}
                                        set -e
                                    """,
                                    returnStatus: true
                                )
                                
                                // Read exit code from file
                                def actualExitCode = 0
                                try {
                                    def exitCodeStr = readFile(exitCodeFile).trim()
                                    actualExitCode = exitCodeStr.toInteger()
                                } catch (Exception e) {
                                    echo "WARNING: Could not read exit code file: ${e.getMessage()}"
                                    actualExitCode = 254  // Default AWS CLI error code
                                }
                                
                                // Read output
                                try {
                                    jobOutput = readFile(outputFile).trim()
                                } catch (Exception e) {
                                    echo "WARNING: Could not read output file: ${e.getMessage()}"
                                    jobOutput = "Error reading output file: ${e.getMessage()}"
                                }
                                
                                // Clean up temp files
                                sh "rm -f ${outputFile} ${exitCodeFile}"
                                
                                echo "=== COMMAND OUTPUT ==="
                                echo "${jobOutput}"
                                echo "=== END OUTPUT ==="
                                
                                // Check exit code
                                if (actualExitCode != 0) {
                                    echo "ERROR: Command failed with exit code ${actualExitCode}"
                                    
                                    // Try to extract detailed error message
                                    if (jobOutput.contains('An error occurred')) {
                                        def errorMatch = jobOutput =~ /An error occurred \\((.*?)\\) when calling the CreateJob operation: (.*)/
                                        if (errorMatch) {
                                            echo "Error Type: ${errorMatch[0][1]}"
                                            echo "Error Message: ${errorMatch[0][2]}"
                                        } else {
                                            echo "Full Output: ${jobOutput}"
                                        }
                                    } else {
                                        echo "Full Output: ${jobOutput}"
                                    }
                                    
                                    error("Failed to create S3 Batch Operations job (exit code: ${actualExitCode})")
                                }
                                
                                // Check if output contains error message (even if exit code was 0)
                                if (jobOutput.contains('An error occurred') || jobOutput.contains('InvalidRequest') || jobOutput.contains('Request invalid')) {
                                    error("AWS CLI returned error: ${jobOutput}")
                                }
                                
                                // Verify we got valid JSON with JobId
                                if (!jobOutput.contains('JobId')) {
                                    error("Unexpected response format. Expected JobId but got: ${jobOutput}")
                                }
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
