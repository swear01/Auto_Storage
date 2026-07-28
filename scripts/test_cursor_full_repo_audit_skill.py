from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[1]
SKILL_DIR = ROOT / ".cursor/skills/full-repo-audit"
SKILL = SKILL_DIR / "SKILL.md"


class CursorFullRepoAuditSkillTests(unittest.TestCase):
    def skill_text(self) -> str:
        self.assertTrue(SKILL.exists(), "missing Cursor full-repo audit skill")
        return SKILL.read_text()

    def test_skill_is_small_portable_and_discoverable(self):
        text = self.skill_text()
        self.assertLessEqual(len(text.splitlines()), 200)
        self.assertRegex(text, r"(?m)^name: full-repo-audit$")
        self.assertRegex(text, r"(?m)^description: .*(whole|full).*(repo|repository)")
        self.assertFalse((SKILL_DIR / "scripts").exists())

    def test_skill_is_report_first_and_never_changes_source_or_git_history(self):
        text = self.skill_text()
        for required in (
            "read-only",
            "Do not edit",
            "Do not commit",
            "Do not push",
            "Do not create a pull request",
            "file:line",
            "P0",
            "P3",
            "PASS",
            "CONCERNS",
            "FAIL",
            "BLOCKED",
        ):
            self.assertIn(required, text)

    def test_skill_researches_primary_sources_and_similar_projects_safely(self):
        text = self.skill_text()
        for required in (
            "similar projects",
            "official documentation",
            "Refined Storage 2",
            "upstream URL",
            "commit or version",
            "license",
            "Never copy",
            "UNVERIFIED",
        ):
            self.assertIn(required, text)

    def test_skill_publishes_exactly_one_deduplicated_github_issue(self):
        text = self.skill_text()
        for required in (
            "exactly one GitHub issue",
            "base commit SHA",
            "deduplicate",
            "gh issue create",
            "gh issue view",
            "GH_TOKEN",
            "Do not create a canary issue",
            "issue URL",
            "Do not create one issue per finding",
            "Resource not accessible by integration",
        ):
            self.assertIn(required, text)

        output_contract = text.split("## GitHub Issue Contract", 1)[1]
        self.assertNotRegex(
            output_contract,
            re.compile(r"\b(?:fix|implement|refactor) the findings\b", re.IGNORECASE),
        )

    def test_notes_document_the_manual_cloud_handoff_and_issue_result(self):
        notes = (ROOT / "docs/notes.md").read_text()
        for required in (
            "Cursor Cloud full-repo audit",
            "cursor-agent",
            "& /full-repo-audit",
            "cursor.com/agents",
            "exactly one GitHub issue",
            "issues:write",
            "GH_TOKEN",
            "Personal",
            "fine-grained",
        ):
            self.assertIn(required, notes)

    def test_structure_lists_the_cursor_skill(self):
        structure = (ROOT / "docs/structure.md").read_text()
        self.assertIn("`.cursor/skills/full-repo-audit/`", structure)
        self.assertIn("Cursor Cloud", structure)


if __name__ == "__main__":
    unittest.main()
