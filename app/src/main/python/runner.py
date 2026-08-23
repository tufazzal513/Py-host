import sys
import runpy
import io
import traceback
import os

def run_project(project_dir, entry_point):
    pkg_dir = os.path.join(project_dir, '.packages')
    if os.path.exists(pkg_dir) and pkg_dir not in sys.path:
        sys.path.insert(0, pkg_dir)
        
    sys.path.insert(0, project_dir)
    old_stdout = sys.stdout
    old_stderr = sys.stderr
    redirected_output = io.StringIO()
    sys.stdout = redirected_output
    sys.stderr = redirected_output
    
    try:
        runpy.run_path(f"{project_dir}/{entry_point}", run_name="__main__")
    except Exception as e:
        traceback.print_exc()
    finally:
        sys.stdout = old_stdout
        sys.stderr = old_stderr
        if project_dir in sys.path:
            sys.path.remove(project_dir)
        if pkg_dir in sys.path:
            sys.path.remove(pkg_dir)
    
    return redirected_output.getvalue()
