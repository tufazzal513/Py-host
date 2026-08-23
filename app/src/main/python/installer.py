import sys
import io
import traceback
import os

def install_requirements(project_dir):
    req_file = os.path.join(project_dir, 'requirements.txt')
    if not os.path.exists(req_file):
        return "Error: requirements.txt not found in project."
    
    with open(req_file, 'r') as f:
        reqs = f.read().strip()
        if not reqs:
            return "requirements.txt is empty. Nothing to install."

    pkg_dir = os.path.join(project_dir, '.packages')
    if not os.path.exists(pkg_dir):
        os.makedirs(pkg_dir)
        
    old_stdout = sys.stdout
    old_stderr = sys.stderr
    redirected_output = io.StringIO()
    sys.stdout = redirected_output
    sys.stderr = redirected_output
    
    try:
        print("Installing dependencies from requirements.txt...")
        import pip._internal.cli.main as pip_main
        exit_code = pip_main.main(['install', '-r', req_file, '--target', pkg_dir])
        if exit_code == 0:
            print("\nDependencies installed successfully.")
        else:
            print(f"\npip install failed with exit code {exit_code}")
    except ImportError:
        print("Error: pip module not found in the environment.")
    except Exception as e:
        traceback.print_exc()
    finally:
        sys.stdout = old_stdout
        sys.stderr = old_stderr
    
    return redirected_output.getvalue()
