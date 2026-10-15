#!/usr/bin/env python3
import argparse
import json
import os
import re
import sys
import time
from watchdog.observers import Observer
from watchdog.events import FileSystemEventHandler


def find_main_classes(workspace_dir, src_dirs):
    """Search for Java files containing 'void main(' and return fully qualified class names."""
    main_classes = []
    main_pattern = re.compile(r'\bvoid\s+main\s*\(')
    package_pattern = re.compile(r'^\s*package\s+([\w\.]+)\s*;', re.MULTILINE)

    for src_dir in src_dirs:
        full_src_path = os.path.join(workspace_dir, src_dir)
        if not os.path.exists(full_src_path):
            continue

        for root, _, files in os.walk(full_src_path):
            for file in files:
                if file.endswith(".java"):
                    file_path = os.path.join(root, file)
                    try:
                        with open(file_path, "r", encoding="utf-8", errors="ignore") as f:
                            content = f.read()
                            if main_pattern.search(content):
                                class_name = os.path.splitext(file)[0]
                                pkg_match = package_pattern.search(content)
                                if pkg_match:
                                    full_class_name = f"{pkg_match.group(1)}.{class_name}"
                                else:
                                    full_class_name = class_name
                                main_classes.append(full_class_name)
                    except Exception as e:
                        print(f"Warning: Could not read {file_path}: {e}", file=sys.stderr)

    return main_classes


def get_source_paths(workspace_dir):
    """Extract source paths from .vscode/settings.json or default to ['src']."""
    settings_path = os.path.join(workspace_dir, ".vscode", "settings.json")
    if os.path.isfile(settings_path):
        try:
            with open(settings_path, "r", encoding="utf-8") as f:
                settings = json.load(f)
                return settings.get("java.project.sourcePaths", ["src"])
        except Exception as e:
            print(f"Warning: Failed to read settings.json ({e}). Using default 'src'.", file=sys.stderr)
    return ["src"]


def update_launch_json(workspace_dir):
    """Scan project for main methods and update .vscode/launch.json."""
    vscode_dir = os.path.join(workspace_dir, ".vscode")
    launch_path = os.path.join(vscode_dir, "launch.json")
    os.makedirs(vscode_dir, exist_ok=True)

    src_dirs = get_source_paths(workspace_dir)
    main_classes = find_main_classes(workspace_dir, src_dirs)
    project_name = os.path.basename(os.path.abspath(workspace_dir))

    configurations = []
    if main_classes:
        for main_class in main_classes:
            configurations.append({
                "type": "java",
                "name": f"Run {main_class.split('.')[-1]}",
                "request": "launch",
                "mainClass": main_class,
                "projectName": project_name,
            })
    else:
        configurations.append({
            "type": "java",
            "name": f"Launch {project_name}",
            "request": "launch",
            "mainClass": "",
            "projectName": project_name,
        })

    launch_data = {
        "version": "0.2.0",
        "configurations": configurations,
    }

    # Only write to file if configurations have actually changed to avoid unnecessary disk writes
    current_data = None
    if os.path.isfile(launch_path):
        try:
            with open(launch_path, "r", encoding="utf-8") as f:
                current_data = json.load(f)
        except Exception:
            pass

    if current_data != launch_data:
        with open(launch_path, "w", encoding="utf-8") as f:
            json.dump(launch_data, f, indent=4)
        print(f"[{time.strftime('%H:%M:%S')}] Updated launch.json with {len(main_classes)} target(s): {', '.join(main_classes) if main_classes else 'None'}")


class JavaSourceHandler(FileSystemEventHandler):
    """Watcher that triggers launch.json updates on .java file modifications."""

    def __init__(self, workspace_dir):
        self.workspace_dir = workspace_dir
        self.last_update = 0

    def on_any_event(self, event):
        # Ignore directory events and non-java files
        if event.is_directory or not event.src_path.endswith(".java"):
            return

        # Debounce rapid file system events (500ms window)
        current_time = time.time()
        if current_time - self.last_update > 0.5:
            self.last_update = current_time
            update_launch_json(self.workspace_dir)


def main():
    parser = argparse.ArgumentParser(
        description="Watch a VS Code Java workspace for source code changes and auto-update launch targets."
    )
    parser.add_argument("workspace_dir", help="Path to the VS Code workspace directory")

    args = parser.parse_args()
    workspace_path = os.path.abspath(args.workspace_dir)

    if not os.path.isdir(workspace_path):
        print(f"Error: Path '{workspace_path}' is not a directory.", file=sys.stderr)
        sys.exit(1)

    # Initial scan on startup
    print(f"Performing initial scan for '{workspace_path}'...")
    update_launch_json(workspace_path)

    # Setup file watcher
    event_handler = JavaSourceHandler(workspace_path)
    observer = Observer()
    observer.schedule(event_handler, workspace_path, recursive=True)
    observer.start()

    print(f"Watching '{workspace_path}' for .java file changes. Press Ctrl+C to stop.")
    try:
        while True:
            time.sleep(1)
    except KeyboardInterrupt:
        observer.stop()
        print("\nWatcher stopped.")
    observer.join()


if __name__ == "__main__":
    main()