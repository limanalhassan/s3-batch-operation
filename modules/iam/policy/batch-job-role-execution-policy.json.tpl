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
        "arn:aws:s3:::${source_bucket}",
        "arn:aws:s3:::${source_bucket}/*"
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
        "arn:aws:s3:::${destination_bucket}",
        "arn:aws:s3:::${destination_bucket}/*"
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
        "arn:aws:s3:::${manifest_bucket}",
        "arn:aws:s3:::${manifest_bucket}/*"
      ]
    },
    {
      "Effect": "Allow",
      "Action": [
        "s3:PutObject",
        "s3:ListBucket"
      ],
      "Resource": [
        "arn:aws:s3:::${report_bucket}",
        "arn:aws:s3:::${report_bucket}/*",
        "arn:aws:s3:::${manifest_bucket}/*"
      ]
    },
    {
      "Effect": "Allow",
      "Action": [
        "s3:PutInventoryConfiguration"
      ],
      "Resource": "arn:aws:s3:::${destination_bucket}"
    }
  ]
}

