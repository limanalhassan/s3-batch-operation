{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "s3:ListAllMyBuckets"
      ],
      "Resource": "*"
    },
    {
      "Effect": "Allow",
      "Action": [
        "s3:GetBucketTagging"
      ],
      "Resource": "arn:aws:s3:::*"
    },
    {
      "Effect": "Allow",
      "Action": [
        "s3:CreateBucket"
      ],
      "Resource": "*"
    },
    {
      "Effect": "Allow",
      "Action": [
        "s3:PutBucketPolicy",
        "s3:PutBucketVersioning",
        "s3:PutBucketEncryption",
        "s3:PutBucketTagging",
        "s3:PutBucketLifecycleConfiguration",
        "s3:PutLifecycleConfiguration",
        "s3:GetBucketLocation",
        "s3:GetBucketVersioning"
      ],
      "Resource": "*"
    },
    {
      "Effect": "Allow",
      "Action": [
        "iam:CreateRole",
        "iam:GetRole"
      ],
      "Resource": "arn:aws:iam::*:role/${name_prefix}-batch-job-role*"
    },
    {
      "Effect": "Allow",
      "Action": [
        "iam:PutRolePolicy",
        "iam:AttachRolePolicy",
        "iam:TagRole"
      ],
      "Resource": "arn:aws:iam::*:role/${name_prefix}-batch-job-role*",
      "Condition": {
        "StringEquals": {
          "iam:AWSServiceName": "batchoperations.s3.amazonaws.com"
        }
      }
    },
    {
      "Effect": "Allow",
      "Action": "iam:PassRole",
      "Resource": "arn:aws:iam::*:role/${name_prefix}-batch-job-role*",
      "Condition": {
        "StringEquals": {
          "iam:PassedToService": "batchoperations.s3.amazonaws.com"
        }
      }
    },
    {
      "Effect": "Allow",
      "Action": [
        "s3control:CreateJob"
      ],
      "Resource": "*"
    }
  ]
}

