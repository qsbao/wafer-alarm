Review all staged and unstaged changes, then commit and push to the remote.

Steps:
1. Run `git status` and `git diff` to see all changes.
2. Stage all relevant changes (exclude secrets, build artifacts, and files in .gitignore).
3. Write a concise commit message that follows the project's existing commit style (e.g. `feat:`, `fix:`, `docs:`). Focus on the "why", not the "what".
4. Commit the changes.
5. Push to the current remote branch. If no upstream is set, push with `-u origin <branch>`.
6. Report the result with the commit hash and remote URL.

If there are no changes to commit, say so and stop.
