package iti.tel.twilio_project;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;

/**
 * Lightweight session check endpoint.
 * Called by HTML pages on load to verify the user is logged in
 * and has the correct role before rendering content.
 *
 * GET /SessionCheckServlet?required=CUSTOMER  → 200 OK if logged in as CUSTOMER, else 401
 * GET /SessionCheckServlet?required=ADMIN     → 200 OK if logged in as ADMIN, else 401/403
 */
public class SessionCheckServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        HttpSession session = request.getSession(false);

        if (session == null || session.getAttribute("user_id") == null) {
            response.setStatus(401);
            response.getWriter().println("{\"error\":\"not_logged_in\"}");
            return;
        }

        String requiredRole = request.getParameter("required");
        String actualRole   = (String) session.getAttribute("user_role");

        if (requiredRole != null && !requiredRole.equalsIgnoreCase(actualRole)) {
            response.setStatus(403);
            response.getWriter().println("{\"error\":\"wrong_role\",\"role\":\"" + actualRole + "\"}");
            return;
        }

        String userName = (String) session.getAttribute("user_name");
        response.setStatus(200);
        response.getWriter().println("{\"ok\":true,\"role\":\"" + actualRole + "\",\"name\":\"" + escJson(userName) + "\"}");
    }

    private String escJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
