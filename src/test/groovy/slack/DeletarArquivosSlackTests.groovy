package slack

import groovy.transform.CompileStatic

@CompileStatic
class DeletarArquivosSlackTests extends GroovyTestCase {

	void testDeletar() {
		DeletarArquivosSlack deletarArquivosSlack = new DeletarArquivosSlack()
		deletarArquivosSlack.deletar()
	}

}
