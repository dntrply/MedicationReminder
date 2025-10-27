# Commit Code Changes

Check in all current code changes following the standard commit process:

## Process Steps:

1. **Review Changes**
   - Run `git status` to see all modified and new files
   - Run `git diff --stat` to get a statistical summary
   - Run `git diff` to review detailed changes

2. **Update Version** (if this is a release)
   - Update `app/build.gradle.kts`:
     - Increment `versionCode` by 1
     - Update `versionName` following semantic versioning:
       - Patch (0.x.Y): Bug fixes only
       - Minor (0.X.0): New features, backwards compatible
       - Major (X.0.0): Breaking changes

3. **Update Documentation**
   - Update `CHANGELOG.md` with a new version section:
     - Add version number and date
     - Categorize changes under: Added, Changed, Fixed, Removed, Technical
     - Use clear, user-focused descriptions
     - Include technical details in Technical section

4. **Stage Changes**
   - Run `git add -A` to stage all changes

5. **Create Commit**
   - Build commit message from git diff understanding:
     - Clear, descriptive title (50 chars or less)
     - Blank line
     - Detailed body explaining WHAT and WHY (not HOW)
     - Group related changes into sections
     - List new files created
     - List modified files with brief explanation
     - End with Claude Code attribution
   - Commit format:
     ```
     Short descriptive title (vX.Y.Z)

     Longer explanation of changes, organized into logical sections.

     Section 1:
     - Detail about change 1
     - Detail about change 2

     Section 2:
     - Detail about change 3

     New Files:
     - path/to/new/file1.kt
     - path/to/new/file2.kt

     Modified Files:
     - path/to/modified/file1.kt - brief explanation
     - path/to/modified/file2.kt - brief explanation

     🤖 Generated with [Claude Code](https://claude.com/claude-code)

     Co-Authored-By: Claude <noreply@anthropic.com>
     ```

6. **Create Git Tag** (for version releases)
   - Run `git tag -a vX.Y.Z -m "Release vX.Y.Z: Brief description"`
   - Tag message should match version and brief summary from CHANGELOG

7. **Verify**
   - Run `git log -1 --oneline` to verify commit
   - Run `git tag -l "vX.Y.Z" -n3` to verify tag (if created)
   - Show summary to user

## Version Numbering Guidelines:

- **Patch (0.9.X)**: Bug fixes, small improvements, no new features
- **Minor (0.X.0)**: New features, enhancements, backwards compatible
- **Major (X.0.0)**: Breaking changes, major overhauls

## CHANGELOG Guidelines:

- **Added**: New features, new capabilities
- **Changed**: Changes to existing functionality, UI updates, refactors
- **Fixed**: Bug fixes, error corrections
- **Removed**: Removed features or functionality
- **Technical**: Database migrations, version bumps, internal changes

## Notes:

- Always review the git diff to understand all changes before committing
- Commit messages should be comprehensive and informative
- Group related changes logically in the commit message
- Include context about WHY changes were made, not just WHAT changed
- For large commits, organize the message into clear sections
