package iti.tel.twilio_project;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.sql.*;

/**
 * Deletes a specific SMS from the logged-in customer's history.
 * Only deletes if the message belongs to the logged-in user (security check).
 * POST /DeleteSmsServlet?message_id=X
 */
public class DeleteSmsServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("user_id") == null) {
            response.setStatus(401);
            return;
        }

        int userId = (Integer) session.getAttribute("user_id");

        String msgIdStr = request.getParameter("message_id");
        if (msgIdStr == null || msgIdStr.isBlank()) {
            response.setStatus(400);
            response.getWriter().println("Missing message_id");
            return;
        }

        int messageId;
        try {
            messageId = Integer.parseInt(msgIdStr);
        } catch (NumberFormatException e) {
            response.setStatus(400);
            response.getWriter().println("Invalid message_id");
            return;
        }

        Connection conn = null;
        try {
            conn = DBConnection.getConnection();

            // Security: only delete if this message belongs to the logged-in user
            PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM sms_messages WHERE message_id = ? AND user_id = ?"
            );
            ps.setInt(1, messageId);
            ps.setInt(2, userId);
            int rows = ps.executeUpdate();

            if (rows > 0) {
                response.setStatus(200);
                response.getWriter().println("Deleted");
            } else {
                response.setStatus(404);
                response.getWriter().println("Not found or unauthorized");
            }

        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(500);
            response.getWriter().println("Server error");
        } finally {
            try { if (conn != null) conn.close(); } catch (Exception ignored) {}
        }
    }
}
