package slack

import groovyx.net.http.*

//import groovy.transform.CompileStatic
//@CompileStatic
class DeletarArquivosSlack {

	private static Integer FILES_PAGE_SIZE = 100
	private String ADMIN_TOKEN = ''

	void deletar() {
		HTTPBuilder http_builder = new RESTClient()

		File fileTokens = new File('/home/zeroglosa/.IntelliJIdea2017.2/config/scratches/scratch.txt')
		Map<String, String> token_users = fileTokens.text.readLines().collectEntries { String linha ->
			String[] partes = linha.split('\t')
			return [(partes[0]), partes[2]]
		}

		String url_list_users = 'https://slack.com/api/users.list?token=' + ADMIN_TOKEN + '&pretty=1'
		String url_list_files = 'https://slack.com/api/files.list'
		String url_delete_file = 'https://slack.com/api/files.delete'

		HttpResponseDecorator response = (HttpResponseDecorator) http_builder.request(url_list_users, Method.GET, ContentType.ANY) {}

		List<Map> usuarios = ((Map) response.data).members

		println("Foram encontrados ${usuarios.size()} no seu slack")

		Date data = new Date()
		long toTime = (data - 30).time
		String ts_to = toTime.toString().substring(0, 10)

		usuarios.each { Map usuario ->
			println("Buscando arquivos do usuário ${usuario.real_name ?: usuario.name}")
			String token_current_user = token_users[usuario.name]
			if (!token_current_user) {
				println("Usuário ${usuario.name} não possui token disponível")
				return
			}

			Set<String> file_ids_current_user = []

			URIBuilder uri_builder = new URIBuilder(url_list_files)
			uri_builder.addQueryParam('user', usuario.id)
			uri_builder.addQueryParam('token', token_current_user)
			uri_builder.addQueryParam('ts_to', ts_to)
			uri_builder.addQueryParam('pretty', '1')

			response = (HttpResponseDecorator) http_builder.request(uri_builder, Method.GET, ContentType.ANY) {}
			Map mapa_response = ((Map) response.data)

			if (mapa_response.error == 'token_revoked') {
				println("Token do usuário ${usuario.name} foi revogado")
				return
			} else if (!mapa_response.paging) {
				println("Houve algum erro não tratado ${mapa_response.toString()}")
				return
			}

			mapa_response.files.each { Map file ->
				file_ids_current_user.add(file.id)
			}

			Integer total = mapa_response.paging.total

			Integer number_pages = total / FILES_PAGE_SIZE
			println("Este usuário possui ${total} arquivos")

			for (int i = 1; i <= number_pages; i++) {
				if (uri_builder.hasQueryParam('page')) {
					uri_builder.removeQueryParam('page')
				}
				uri_builder.addQueryParam('page', i)

				response = (HttpResponseDecorator) http_builder.request(uri_builder, Method.GET, ContentType.ANY) {}
				mapa_response = ((Map) response.data)

				mapa_response.files.each { Map file ->
					file_ids_current_user.add(file.id)
				}
			}

			println("Iniciando deleção dos arquivos do usuário ${usuario.real_name}")
			uri_builder = new URIBuilder(url_delete_file)
			uri_builder.addQueryParam('token', token_current_user)

			file_ids_current_user.eachWithIndex { String file_id, int idx ->
				try {
					if (uri_builder.hasQueryParam('file')) {
						uri_builder.removeQueryParam('file')
					}
				} catch (Exception ignored) {

				}
				uri_builder.addQueryParam('file', file_id)

				try {
					response = (HttpResponseDecorator) http_builder.request(uri_builder, Method.POST, ContentType.ANY) {}
				} catch (Exception e) {
					println(e)
				}
				mapa_response = ((Map) response.data)
				if (idx % 20 == 0) {
					println("Deletados ${idx} arquivos deste usuário até o momento")
				}
			}
		}
	}


}
