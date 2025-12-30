# Jenkins Seed Job Configuration

This directory contains Job DSL scripts for managing Jenkins configuration as code.

## What is a Seed Job?

A **seed job** is a Jenkins job that uses the [Job DSL Plugin](https://plugins.jenkins.io/job-dsl/) to programmatically create and manage other Jenkins jobs, folders, views, and configurations from Groovy scripts stored in version control.

## Benefits

- ✅ **Infrastructure as Code**: All Jenkins configuration in Git
- ✅ **Reproducible**: Same setup across dev/staging/prod
- ✅ **Scalable**: Create many jobs from templates
- ✅ **Maintainable**: Update all jobs by updating scripts
- ✅ **Organized**: Automatic folder structure

## Structure

```
jenkins/
├── README.md                    # This file
├── seed-job/                    # Seed job scripts
│   ├── seed.groovy             # Main seed script (entry point)
│   ├── folders.groovy          # Folder definitions
│   ├── jobs.groovy             # Job definitions
│   └── views.groovy            # View definitions (optional)
└── pipelines/                   # Actual pipeline Jenkinsfiles
    └── (your Jenkinsfiles go here)
```

## How It Works

1. **Install Job DSL Plugin** (via Ansible)
2. **Create Seed Job** manually (one-time setup)
3. **Seed Job Runs** → Checks out this repo → Executes `seed.groovy`
4. **seed.groovy** → Loads `folders.groovy`, `jobs.groovy`, etc.
5. **Folders & Jobs Created** automatically in Jenkins

## Setup Instructions

### Step 1: Install Job DSL Plugin

The Job DSL plugin will be installed via Ansible (configured in `ansible/group_vars/all.yml`).

### Step 2: Create the Seed Job Manually (One-Time)

1. Go to Jenkins → **New Item**
2. Name it: `seed-job`
3. Select: **Freestyle project**
4. Click **OK**
5. Configure:
   - **Source Code Management**: Git
     - Repository URL: Your Git repo URL
     - Branch: `*/main` (or your default branch)
   - **Build Triggers**: 
     - ☑ Poll SCM: `H * * * *` (runs every hour)
     - OR ☑ GitHub hook trigger (if using GitHub)
   - **Build**:
     - Add build step: **Process Job DSLs**
     - DSL Scripts: `jenkins/seed-job/seed.groovy`
     - ☑ Use Groovy Sandbox (recommended for security)
6. Click **Save**

### Step 3: Run the Seed Job

1. Click **Build Now** on the seed-job
2. Check the console output
3. You should see folders and jobs being created!

## Understanding the Scripts

### `seed.groovy`
The main entry point that:
- Loads other Groovy files
- Sets up the overall structure
- Can include conditional logic

### `folders.groovy`
Defines folder hierarchy:
```groovy
folder('S3-Batch-Operations') {
    displayName('S3 Batch Operations')
    description('Jobs for S3 batch operations')
}
```

### `jobs.groovy`
Defines actual Jenkins jobs:
```groovy
pipelineJob('S3-Batch-Operations/Dev/Example-Pipeline') {
    definition {
        cps {
            script(readFileFromWorkspace('pipelines/example.groovy'))
        }
    }
}
```

## Adding New Jobs

1. Edit `jenkins/seed-job/jobs.groovy`
2. Add your job definition
3. Commit and push to Git
4. Seed job will automatically pick it up (if polling) or trigger manually

## Learning Resources

- [Job DSL Plugin Documentation](https://github.com/jenkinsci/job-dsl-plugin/wiki)
- [Job DSL API Reference](https://jenkinsci.github.io/job-dsl-plugin/)
- [Job DSL Examples](https://github.com/jenkinsci/job-dsl-plugin/wiki/Job-DSL-Examples)

## Troubleshooting

### Seed Job Fails
- Check console output for errors
- Verify Groovy syntax
- Check file paths are correct
- Ensure Job DSL plugin is installed

### Jobs Not Created
- Check seed job ran successfully
- Verify DSL scripts are in correct location
- Check Jenkins logs: `/var/log/jenkins/jenkins.log`

### Permission Errors
- Ensure Jenkins has permissions to create jobs
- Check if using Groovy Sandbox (may need to approve scripts)

