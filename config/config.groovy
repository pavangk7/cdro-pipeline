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
          'config': 'GitHub_Global_Config', // Use your configuration name here
          'destFolder': 'src',
          'overwrite': '1',
          'repoUrl': 'https://github.com/your-org/your-repo.git'
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
            cd "$[/myWorkspace/agentWorkspace]/src"
            if [ -f "myscript.sh" ]; then
                chmod +x myscript.sh
                ./myscript.sh
            elif [ -f "main.py" ]; then
                python3 main.py
            fi
          '''.stripIndent()
        ]
      }

      // Task 3: NEW TASK - Execute Next Python Script
      task 'Execute_Next_Python_Script', {
        taskType = 'command'
        actualParameter = [
          'command': '''\
            echo "Moving to workspace to run the next Python script..."
            cd "$[/myWorkspace/agentWorkspace]/src"

            # Check if the next script exists before running it
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
