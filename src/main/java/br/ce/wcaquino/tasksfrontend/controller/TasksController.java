package br.ce.wcaquino.tasksfrontend.controller;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.ce.wcaquino.tasksfrontend.model.Todo;

@Controller
public class TasksController {
	
	@Value("${backend.host}")
	private String BACKEND_HOST;

	@Value("${backend.port}")
	private String BACKEND_PORT;
	
	@Value("${app.version}")
	private String VERSION;
	
	public String getBackendURL() {
		return "http://" + BACKEND_HOST + ":" + BACKEND_PORT;
	}
	
	@GetMapping("")
	public String index(Model model) {
		model.addAttribute("todos", getTodos());
		if(VERSION.startsWith("build"))
			model.addAttribute("version", VERSION);
		return "index";
	}
	
	@GetMapping("add")
	public String add(Model model) {
		model.addAttribute("todo", new Todo());
		return "add";
	}

	@PostMapping("save")
	public String save(Todo todo, Model model) {
		try {
			RestTemplate restTemplate = new RestTemplate();
			restTemplate.postForObject(
					getBackendURL() + "/tasks-backend/todo", todo, Object.class);
			model.addAttribute("success", "Success!");
			return "index";
		} catch(Exception e) {
			model.addAttribute("error", extractErrorMessage(e));
			model.addAttribute("todo", todo);
			return "add";
		} finally {
			model.addAttribute("todos", getTodos());
		}
	}

	/**
	 * TODO (business decision): what should the user see when saving a todo fails?
	 *
	 * Two distinct failure shapes reach here:
	 *  - RestClientResponseException: the backend responded with an HTTP error status
	 *    (e.g. 400) and a JSON body like {"message":"Fill the due date"} — this is the
	 *    backend rejecting bad input, already parsed into `backendMessage` below.
	 *  - anything else (e.g. ResourceAccessException): the backend couldn't be reached
	 *    at all (down, timeout, DNS) — there is no JSON body, `backendMessage` is null.
	 *
	 * Trade-off: surfacing the backend's own message is more helpful for validation
	 * errors (tells the user exactly what to fix) but couples this UI to the backend's
	 * wording, and is meaningless/absent for connectivity failures. Decide how to
	 * present each case (e.g. show `backendMessage` when present, otherwise a fixed
	 * "couldn't reach the server" copy) and whether to log unexpected exceptions.
	 */
	private String extractErrorMessage(Exception e) {
		String backendMessage = null;
		if (e instanceof RestClientResponseException responseException) {
			try {
				Map<?, ?> body = new ObjectMapper()
						.readValue(responseException.getResponseBodyAsString(), Map.class);
				Object message = body.get("message");
				backendMessage = message == null ? null : message.toString();
			} catch (JsonProcessingException parseError) {
				backendMessage = null;
			}
		}

		// Placeholder fallback so the app never 500s while this TODO is pending.
		return backendMessage != null ? backendMessage : "Failed to save the task";
	}
	
	@GetMapping("delete/{id}")
	public String delete(@PathVariable Long id, Model model) {
		RestTemplate restTemplate = new RestTemplate();
		restTemplate.delete(getBackendURL() + "/tasks-backend/todo/" + id);			
		model.addAttribute("success", "Success!");
		model.addAttribute("todos", getTodos());
		return "index";
	}

	
	@SuppressWarnings("unchecked")
	private List<Todo> getTodos() {
		RestTemplate restTemplate = new RestTemplate();
		return restTemplate.getForObject(
				getBackendURL() + "/tasks-backend/todo", List.class);
	}
}
