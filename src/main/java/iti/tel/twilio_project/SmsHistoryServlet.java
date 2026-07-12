package iti.tel.twilio_project;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.*;

/**
 * Returns the SMS history for the logged-in customer as an HTML fragment (JSON-friendly).
 * Supports search by: from, to, date range.
 * GET  /SmsHistoryServlet          → full history
 * GET  /SmsHistoryServlet?from=X   → filter by sender
 * GET  /SmsHistoryServlet?to=X     → filter by receiver
 * GET  /SmsHistoryServlet?start=YYYY-MM-DD&end=YYYY-MM-DD → date range
 */
public class SmsHistoryServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("user_id") == null) {
            response.setStatus(401);
            response.getWriter().println("[]");
            return;
        }

        int    userId = (Integer) session.getAttribute("user_id");
        String from   = request.getParameter("from");
        String to     = request.getParameter("to");
        String start  = request.getParameter("start");
        String end    = request.getParameter("end");

        StringBuilder sql = new StringBuilder(
            "SELECT message_id, sender_from, receiver_to, body, message_date " +
            "FROM sms_messages WHERE user_id = ?"
        );

        if (from != null && !from.isBlank())  sql.append(" AND sender_from ILIKE ?");
        if (to   != null && !to.isBlank())    sql.append(" AND receiver_to ILIKE ?");
        if (start != null && !start.isBlank()) sql.append(" AND message_date >= ?::date");
        if (end   != null && !end.isBlank())   sql.append(" AND message_date <= (?::date + INTERVAL '1 day')");

        sql.append(" ORDER BY message_date DESC");

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();

        Connection conn = null;
        try {
            conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql.toString());

            int idx = 1;
            ps.setInt(idx++, userId);
            if (from  != null && !from.isBlank())  ps.setString(idx++, "%" + from + "%");
            if (to    != null && !to.isBlank())     ps.setString(idx++, "%" + to + "%");
            if (start != null && !start.isBlank())  ps.setString(idx++, start);
            if (end   != null && !end.isBlank())    ps.setString(idx++, end);

            ResultSet rs = ps.executeQuery();

            out.print("[");
            boolean first = true;
            while (rs.next()) {
                if (!first) out.print(",");
                first = false;
                out.printf(
                    "{\"id\":%d,\"from\":\"%s\",\"to\":\"%s\",\"body\":\"%s\",\"date\":\"%s\"}",
                    rs.getInt("message_id"),
                    escapeJson(rs.getString("sender_from")),
                    escapeJson(rs.getString("receiver_to")),
                    escapeJson(rs.getString("body")),
                    rs.getTimestamp("message_date").toString()
                );
            }
            out.print("]");

        } catch (Exception e) {
            e.printStackTrace();
            out.print("[]");
        } finally {
            try { if (conn != null) conn.close(); } catch (Exception ignored) {}
        }
    }

    /** Escapes special characters for JSON string output */
    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
