import sys
import io
import traceback
import os

class StreamRedirector:
    def __init__(self, callback):
        self.callback = callback
    def write(self, text):
        self.callback.onOutput(text)
    def flush(self):
        pass

def install_requirements(project_dir, callback):
    req_file = os.path.join(project_dir, 'requirements.txt')
    if not os.path.exists(req_file):
        callback.onOutput("Error: requirements.txt not found in project.\n")
        return
    
    with open(req_file, 'r') as f:
        reqs = f.read().strip()
        if not reqs:
            callback.onOutput("requirements.txt is empty. Nothing to install.\n")
            return

    dependencies = []
    for line in reqs.split('\n'):
        line = line.strip()
        if line and not line.startswith('#'):
            dependencies.append(line)

    if not dependencies:
        callback.onOutput("No valid dependencies found in requirements.txt.\n")
        return

    callback.onOutput(f"Detected dependencies: {', '.join(dependencies)}\n")

    pkg_dir = os.path.join(project_dir, '.packages')
    if not os.path.exists(pkg_dir):
        os.makedirs(pkg_dir)
        
    old_stdout = sys.stdout
    old_stderr = sys.stderr
    
    redirector = StreamRedirector(callback)
    sys.stdout = redirector
    sys.stderr = redirector
    
    try:
        import pip._internal.cli.main as pip_main
        
        callback.onOutput("\nStarting installation process...\n")
        
        for dep in dependencies:
            callback.onOutput(f"\n---> Installing {dep} ...\n")
            
            dep_output = io.StringIO()
            sys.stdout = dep_output
            sys.stderr = dep_output
            
            exit_code = pip_main.main(['install', dep, '--target', pkg_dir, '--upgrade'])
            
            sys.stdout = redirector
            sys.stderr = redirector
            
            output_str = dep_output.getvalue()
            
            if exit_code == 0:
                callback.onOutput(f"[SUCCESS] {dep} installed successfully.\n")
            else:
                callback.onOutput(f"[FAILED] Could not install {dep}.\n")
                
                if "No matching distribution found" in output_str:
                    callback.onOutput(f"Reason: Package '{dep}' not found or no compatible version exists for this Python environment.\n")
                elif "ResolutionImpossible" in output_str or "conflict" in output_str.lower():
                    callback.onOutput(f"Reason: Version conflict. '{dep}' requires dependencies that conflict with other packages.\n")
                elif "Failed building wheel" in output_str or "gcc" in output_str:
                    callback.onOutput(f"Reason: '{dep}' requires native C/C++ compilation which is not supported natively in this Android environment.\n")
                elif "NewConnectionError" in output_str or "Network" in output_str:
                    callback.onOutput("Reason: Network error. Please check your internet connection.\n")
                else:
                    callback.onOutput(f"Reason: Unknown error. Details:\n")
                    error_lines = [line for line in output_str.split('\n') if "ERROR:" in line or "Exception:" in line]
                    if error_lines:
                        callback.onOutput("\n".join(error_lines) + "\n")
                    else:
                        callback.onOutput("Check logs for more details.\n")
                        
        callback.onOutput("\nDependency installation phase completed.\n")
        
    except ImportError:
        callback.onOutput("Error: pip module not found in the environment.\n")
    except Exception as e:
        callback.onOutput("An unexpected error occurred:\n")
        callback.onOutput(traceback.format_exc())
    finally:
        sys.stdout = old_stdout
        sys.stderr = old_stderr
