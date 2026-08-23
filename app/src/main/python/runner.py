import sys
import runpy
import traceback
import os

class StreamRedirector:
    def __init__(self, callback):
        self.callback = callback
    def write(self, text):
        self.callback.onOutput(text)
    def flush(self):
        pass

class StdinRedirector:
    def __init__(self, input_provider, callback):
        self.input_provider = input_provider
        self.callback = callback
    def readline(self):
        res = self.input_provider.requestInput()
        self.callback.onOutput(res + "\n")
        return res + "\n"

def run_project_stream(project_dir, entry_point, callback, input_provider):
    pkg_dir = os.path.join(project_dir, '.packages')
    if os.path.exists(pkg_dir) and pkg_dir not in sys.path:
        sys.path.insert(0, pkg_dir)
        
    sys.path.insert(0, project_dir)
    old_stdout = sys.stdout
    old_stderr = sys.stderr
    old_stdin = sys.stdin
    
    redirector = StreamRedirector(callback)
    sys.stdout = redirector
    sys.stderr = redirector
    sys.stdin = StdinRedirector(input_provider, callback)
    
    try:
        runpy.run_path(f"{project_dir}/{entry_point}", run_name="__main__")
    except Exception as e:
        traceback.print_exc()
    finally:
        sys.stdout = old_stdout
        sys.stderr = old_stderr
        sys.stdin = old_stdin
        if project_dir in sys.path:
            sys.path.remove(project_dir)
        if pkg_dir in sys.path:
            sys.path.remove(pkg_dir)
