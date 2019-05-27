package slack

import groovyx.net.http.*

class DeletarArquivosSlack {

	private static Integer FILES_PAGE_SIZE = 100
	private String ADMIN_TOKEN

	void deletar() {
		List<String> specific_users = [''].findAll() //preencher nome dos usuários se desejar apagar TODOS os arquivos deles
		long total_liberado = 0
		HTTPBuilder http_builder = new RESTClient()

		File adminToken = new File('/media/WORK/temp/deletar-arquivos-slack/src/main/groovy/slack/adm_token')
		ADMIN_TOKEN = adminToken.text

		File fileTokens = new File('/media/WORK/temp/deletar-arquivos-slack/src/main/groovy/slack/tokens.txt')
		Map<String, String> token_users = fileTokens.text.readLines().collectEntries { String linha ->
			String[] partes = linha.split('\t')
			return [(partes[0]), partes[2]]
		}

		String url_list_users = 'https://slack.com/api/users.list?token=' + ADMIN_TOKEN + '&pretty=1'
		String url_list_files = 'https://slack.com/api/files.list'
		String url_delete_file = 'https://slack.com/api/files.delete'

		HttpResponseDecorator response = (HttpResponseDecorator) http_builder.request(url_list_users, Method.GET, ContentType.ANY) {}

		List<Map> usuarios = ((Map) response.data).members.sort { Map usuario -> usuario.real_name ?: usuario.name }
		int maxSizeName = usuarios.collect { Map usuario -> (usuario.real_name ?: usuario.name).toString().size() }.max()

		println("Foram encontrados ${usuarios.size()} usuários no seu slack")

		Date data = new Date()
		long toTime = (data - 15).time
		if (specific_users) {
			toTime = data.time
		}
		String ts_to = toTime.toString().substring(0, 10)

		usuarios.eachWithIndex { Map usuario, int idxUsuario ->
			if (specific_users && !specific_users.contains(usuario.name)) {
				return
			}
			String prefix = "(${idxUsuario + 1}/${usuarios.size()}) [${(usuario.real_name ?: usuario.name).toString().padRight(maxSizeName)}]"
			println("${prefix} Buscando arquivos do usuário")
			String token_current_user = token_users[usuario.name]
			if (!token_current_user) {
				if (usuario.deleted) {
					println("${prefix} foi removido do slack")
				} else {
					println("${prefix} não possui token disponível")
				}
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
				println("${prefix} Token do usuário foi revogado")
				return
			} else if (!mapa_response.paging) {
				println("Houve algum erro não tratado ${mapa_response.toString()}")
				return
			}

			mapa_response.files.each { Map file ->
				file_ids_current_user.add(file.id)
			}

			Integer total = mapa_response.paging.total

			Integer number_pages = (total / FILES_PAGE_SIZE) + 1
			println("${prefix} possui ${total} arquivos")

			long size_this_user = 0
			for (int i = 1; i <= number_pages; i++) {
				if (uri_builder.hasQueryParam('page')) {
					uri_builder.removeQueryParam('page')
				}
				uri_builder.addQueryParam('page', i)

				response = (HttpResponseDecorator) http_builder.request(uri_builder, Method.GET, ContentType.ANY) {}
				mapa_response = ((Map) response.data)

				mapa_response.files.each { Map file ->
					total_liberado += file.size
					size_this_user += file.size
					file_ids_current_user.add(file.id)
				}
			}
			println(prefix + " serão liberados ${humanReadableByteCount(size_this_user)} deste usuário")
			println("${prefix} foram encontrados ${file_ids_current_user.size()} arquivos")
			println("${prefix} Iniciando deleção dos arquivos")
			uri_builder = new URIBuilder(url_delete_file)
			uri_builder.addQueryParam('token', token_current_user)

			file_ids_current_user.eachWithIndex { String file_id, int idxFiles ->
				try {
					if (uri_builder.hasQueryParam('file')) {
						uri_builder.removeQueryParam('file')
					}
				} catch (Exception ignored) {
					println()
				}
				uri_builder.addQueryParam('file', file_id)

				try {
					response = (HttpResponseDecorator) http_builder.request(uri_builder, Method.POST, ContentType.ANY) {}
				} catch (Exception e) {
					println(e)
				}
				mapa_response = ((Map) response.data)
				if (idxFiles % 20 == 0) {
					println("${prefix} deletados ${idxFiles} arquivos até o momento")
				}
			}
		}
		println("Foram liberados ${humanReadableByteCount(total_liberado)}")
	}

	String humanReadableByteCount(long bytes) {
		int unit = 1024;
		if (bytes < unit) return bytes + " B";
		int exp = (int) (Math.log(bytes) / Math.log(unit));
		String pre = ('kMGTPE').charAt(exp - 1)
		return String.format("%.1f %sB", bytes / Math.pow(unit, exp), pre);
	}
}
