package link.infra.packwiz.installer.task.formats.packwizv1

import link.infra.packwiz.installer.metadata.hash.Hash
import link.infra.packwiz.installer.target.path.PackwizPath

data class PackwizV1PackFile(val name: String, val indexPath: PackwizPath, val indexHash: Hash)
