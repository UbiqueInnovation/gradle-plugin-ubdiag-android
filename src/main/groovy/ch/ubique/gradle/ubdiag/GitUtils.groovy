package ch.ubique.gradle.ubdiag

class GitUtils {

	/**
	 * Get the branch name from version control.
	 * @return
	 */
	static String obtainBranch() {
		String cmdGitBranch = "git rev-parse --abbrev-ref HEAD"
		String branchName = cmdGitBranch.execute().text.trim()

		if (branchName.isEmpty()) {
			branchName = "develop"
		}
		return branchName
	}

}
