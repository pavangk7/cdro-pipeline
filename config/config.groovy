// 1. Create the Git Configuration (Stores your credentials securely)
pluginConfiguration pluginName: 'EC-Git', 
    configName: 'GitHub_Global_Config', 
    description: 'Configuration for GitHub Access',
    credential: [
        userName: 'your-git-username',              // REPLACE with your Git username
        password: 'your-personal-access-token'     // REPLACE with your Git PAT / Password
    ]

// 2. Define the Project, Pipeline, and Tasks
project 'My_Automation_Project', {

  pipeline 'Cloudless_CDRO_Pipeline', {
    description = 'Pipeline to check out source code and execute multiple scripts.'

    stage 'Build_and_Run', {
      colorCode = '#00bee6'

      // Task 1: Source Code Checkout
      task 'Source_Checkout', {
        actualParameter = [
          'branch': 'main',
          'clone': '1',
          'config': 'GitHub_Global_Config',        // Matches the configName defined above
          'destFolder': 'src',
          'overwrite': '1',
          'repoUrl': 'https://github.com/your-org/your-repo.git' // REPLACE with your repo URL
        ]
        subpluginKey = 'EC-Git'
        subprocedure = 'CheckoutCode'
        taskType = 'plugin'
      }

      // Task 2: Execute First Script (Shell / Python)
      task 'Execute_First_Script', {
        taskType = 'command'
        actualParameter = [
          'command': '''\
            echo "=== Starting Task 1: First Script ==="
            cd "$[/myWorkspace/agentWorkspace]/src"
            
            if [ -f "myscript.sh" ]; then
                echo "Found myscript.sh. Running it..."
                chmod +x myscript.sh
                ./myscript.sh
            elif [ -f "main.py" ]; then
                echo "Found main.py. Running it..."
                python3 main.py
            else
                echo "No initial shell or python script found. Skipping."
            fi
          '''.stripIndent()
        ]
      }

      // Task 3: Execute Next Python Script
      task 'Execute_Next_Python_Script', {
        taskType = 'command'
        actualParameter = [
          'command': '''\
            echo "=== Starting Task 2: Next Python Script ==="
            cd "$[/myWorkspace/agentWorkspace]/src"

            # REPLACE 'next_script.py' with your actual secondary python script name
            if [ -f "next_script.py" ]; then
                echo "Executing next_script.py..."
                python3 next_script.py
            else
                echo "Error: next_script.py not found!"
                exit 1
            fi
          '''.stripIndent()
        ]
      }
      
    }
  }
}
