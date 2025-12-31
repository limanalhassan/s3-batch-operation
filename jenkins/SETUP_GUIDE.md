# Jenkins Seed Job Setup Guide

Step-by-step guide to set up the seed job in Jenkins.

## Prerequisites

✅ Jenkins is installed and running  
✅ Job DSL plugin is installed (via Ansible)  
✅ Git repository is accessible from Jenkins  
✅ You have admin access to Jenkins

## Step-by-Step Setup

### Step 1: Verify Job DSL Plugin

1. Go to Jenkins → **Manage Jenkins** → **Manage Plugins**
2. Click on **Installed** tab
3. Search for "Job DSL Plugin"
4. Verify it's installed (should show version number)

If not installed:
- Go to **Available** tab
- Search for "Job DSL Plugin"
- Check the box and click **Install without restart**

### Step 2: Create the Seed Job

**⚠️ CRITICAL: The seed job MUST be a Freestyle project, NOT a Pipeline!**

1. Go to Jenkins home page
2. Click **New Item** (or **Create a job**)
3. Enter name: `seed-job`
4. **IMPORTANT**: Select **Freestyle project** (NOT Pipeline!)
5. Click **OK**

**Why Freestyle?** Job DSL only works in Freestyle jobs with "Process Job DSLs" build step. Pipeline jobs don't support `readFileFromWorkspace` in the same way.

### Step 3: Configure Source Code Management

1. In the seed-job configuration:
2. Scroll to **Source Code Management**
3. Select **Git**
4. Enter your repository URL:
   ```
   https://github.com/your-username/s3-batch-operations.git
   ```
   OR if using SSH:
   ```
   git@github.com:your-username/s3-batch-operations.git
   ```
5. **Credentials**: 
   - If private repo, add credentials (GitHub token or SSH key)
   - Click **Add** → **Jenkins** → Configure credentials
6. **Branch Specifier**: `*/main` (or `*/master` if that's your default branch)

### Step 4: Configure Build Triggers

1. Scroll to **Build Triggers**
2. Choose one:

   **Option A: Poll SCM (Recommended for learning)**
   - ☑ Poll SCM
   - Schedule: `H * * * *` (runs every hour)
   - This checks for changes and runs automatically

   **Option B: GitHub Webhook (Recommended for production)**
   - ☑ GitHub hook trigger for GITScm polling
   - Requires webhook setup in GitHub (see below)

   **Option C: Manual Only**
   - Don't check any triggers
   - Run manually when needed

### Step 5: Add Job DSL Build Step

1. Scroll to **Build**
2. Click **Add build step** → **Process Job DSLs**
3. Configure:
   - **DSL Scripts**: `jenkins/seed-job/seed.groovy`
   - **Use Groovy Sandbox**: 
     - ☑ Check this box (recommended for security)
     - **BUT**: You must also configure "Run as specific user" (see below)
   - **Additional classpath**: (leave empty unless needed)

### Step 5b: Configure Job to Run as Specific User (Required for Sandbox)

**IMPORTANT**: If you enable Groovy Sandbox, you MUST configure the job to run as a specific user.

1. Scroll to **Build Environment** section (above Build steps)
2. ☑ Check **Run as specific user**
3. Enter a username (e.g., `admin` or your Jenkins username)
4. This is required for Groovy Sandbox security

**Alternative**: If you don't want to configure a user, uncheck "Use Groovy Sandbox" (less secure but works)

### Step 6: Save and Run

1. Click **Save**
2. You should see the seed-job in your Jenkins dashboard
3. Click **Build Now** to run it for the first time
4. Click on the build number to see console output

### Step 7: Verify Results

After the seed job runs successfully:

1. Go back to Jenkins home page
2. You should see:
   - **S3-Batch-Operations** folder
   - Inside it: **Dev**, **Staging**, **Prod** folders
   - Inside **Dev**: Example jobs

3. If jobs don't appear:
   - Check seed job console output for errors
   - Verify file paths are correct
   - Check Jenkins logs

## Setting Up GitHub Webhook (Optional)

If you want automatic updates when you push to Git:

1. Go to your GitHub repository
2. **Settings** → **Webhooks** → **Add webhook**
3. **Payload URL**: `http://your-jenkins-url:8080/github-webhook/`
4. **Content type**: `application/json`
5. **Events**: Select **Just the push event**
6. Click **Add webhook**

## Troubleshooting

### "You must configure the DSL job to run as a specific user"

**Problem**: Groovy Sandbox is enabled but job is running as SYSTEM

**Solution**:
1. Go to seed-job → **Configure**
2. Scroll to **Build Environment** section
3. ☑ Check **Run as specific user**
4. Enter your Jenkins username (e.g., `admin`)
5. Save and run again

**Alternative**: Uncheck "Use Groovy Sandbox" in the Job DSL build step (less secure but works without user config)

### "No such DSL method 'readFileFromWorkspace' found"

**Problem**: Seed job is configured as a Pipeline instead of Freestyle

**Solution**:
1. Delete the seed job
2. Recreate it as a **Freestyle project** (NOT Pipeline!)
3. Add "Process Job DSLs" build step
4. Run again

**Alternative**: Use `seed-inline.groovy` which has everything in one file (no file reading needed)

### Seed Job Fails with "File not found"

**Problem**: Can't find `jenkins/seed-job/seed.groovy`

**Solution**:
- Verify the file path is correct
- Check that the file is committed to Git
- Ensure the branch is correct in SCM config
- Try using absolute path: `**/seed.groovy`

### "Script not yet approved for use" Error

**Problem**: Jenkins requires script approval when using Groovy Sandbox

**Solution**:
1. Go to **Manage Jenkins** → **In-process Script Approval**
2. You'll see pending script approvals
3. Click **Approve** for each pending script
4. Re-run the seed job

**Alternative (Less Secure)**: Uncheck "Use Groovy Sandbox" in the Job DSL build step configuration

**Note**: You may need to approve scripts multiple times as the seed job evolves. This is normal Jenkins security behavior.

### Jobs Created But Not Visible

**Problem**: Jobs are created but you can't see them

**Solution**:
- Refresh the Jenkins page
- Check if you have view permissions
- Look in the correct folder
- Check Jenkins logs: `/var/log/jenkins/jenkins.log`

### "No such DSL method" Error

**Problem**: Using DSL method that doesn't exist

**Solution**:
- Check [Job DSL API Reference](https://jenkinsci.github.io/job-dsl-plugin/)
- Verify plugin is installed
- Check syntax in your Groovy file

### Seed Job Runs But Nothing Happens

**Problem**: Seed job succeeds but no jobs created

**Solution**:
- Check console output for warnings
- Verify `folders.groovy` and `jobs.groovy` are being loaded
- Check for syntax errors in Groovy files
- Look for "Nothing changed" message (means jobs already exist)

## Next Steps

Once the seed job is working:

1. **Customize folders**: Edit `folders.groovy` to match your needs
2. **Add real jobs**: Edit `jobs.groovy` with your actual job definitions
3. **Create Jenkinsfiles**: Add pipeline scripts in `jenkins/pipelines/`
4. **Add views**: Uncomment views in `seed.groovy` if you want custom views
5. **Version control**: Commit and push changes, seed job will pick them up

## Learning Path

1. ✅ **Start simple**: Get the seed job working with example jobs
2. ✅ **Modify examples**: Change parameters, triggers, etc.
3. ✅ **Add your own jobs**: Create jobs for your S3 operations
4. ✅ **Use Jenkinsfiles**: Move complex logic to separate Jenkinsfiles
5. ✅ **Advanced features**: Add notifications, parallel stages, etc.

## Resources

- [Job DSL Plugin Wiki](https://github.com/jenkinsci/job-dsl-plugin/wiki)
- [Job DSL API Reference](https://jenkinsci.github.io/job-dsl-plugin/)
- [Jenkins Pipeline Documentation](https://www.jenkins.io/doc/book/pipeline/)

