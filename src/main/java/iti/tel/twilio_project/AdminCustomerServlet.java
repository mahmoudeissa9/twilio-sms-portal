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
 * Admin-only servlet for customer management and statistics.
 *
 * GET  /AdminCustomerServlet               → list all customers (JSON)
 * GET  /AdminCustomerServlet?id=X          → view single customer (JSON)
 * GET  /AdminCustomerServlet?action=stats  → SMS count per customer (JSON)
 * POST /AdminCustomerServlet?action=edit   → update customer
 * POST /AdminCustomerServlet?action=delete → delete customer
 */
public class AdminCustomerServlet extends HttpServlet {

    // ── Security guard ─────────────────────────────────────────
    private boolean isAdmin(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        HttpSession session = request.getSession(false);
        if (session == null || !"ADMIN".equals(session.getAttribute("user_role"))) {
            response.setStatus(403);
            response.getWriter().println("{\"error\":\"Forbidden\"}");
            return false;
        }
        return true;
    }

    // ── GET handler ────────────────────────────────────────────
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        if (!isAdmin(request, response)) return;

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();

        String action   = request.getParameter("action");
        String idParam  = request.getParameter("id");

        Connection conn = null;
        try {
            conn = DBConnection.getConnection();

            // --- Statistics: SMS count per customer ---
            if ("stats".equals(action)) {
                PreparedStatement ps = conn.prepareStatement(
                    "SELECT u.id, u.name, u.email, COUNT(s.message_id) AS sms_count " +
                    "FROM users u " +
                    "LEFT JOIN sms_messages s ON u.id = s.user_id " +
                    "WHERE u.role = 'CUSTOMER' " +
                    "GROUP BY u.id, u.name, u.email " +
                    "ORDER BY sms_count DESC"
                );
                ResultSet rs = ps.executeQuery();
                out.print("[");
                boolean first = true;
                while (rs.next()) {
                    if (!first) out.print(",");
                    first = false;
                    out.printf("{\"id\":%d,\"name\":\"%s\",\"email\":\"%s\",\"sms_count\":%d}",
                        rs.getInt("id"),
                        esc(rs.getString("name")),
                        esc(rs.getString("email")),
                        rs.getInt("sms_count")
                    );
                }
                out.print("]");
                return;
            }

            // --- Single customer view ---
            if (idParam != null && !idParam.isBlank()) {
                int custId = Integer.parseInt(idParam);
                PreparedStatement ps = conn.prepareStatement(
                    "SELECT id, name, email, phone_number_msisdn, birthday, job, address, " +
                    "       twilio_sid, allowed_sender_id, is_verified, created_at " +
                    "FROM users WHERE id = ? AND role = 'CUSTOMER'"
                );
                ps.setInt(1, custId);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    out.printf(
                        "{\"id\":%d,\"name\":\"%s\",\"email\":\"%s\",\"phone\":\"%s\"," +
                        "\"birthday\":\"%s\",\"job\":\"%s\",\"address\":\"%s\"," +
                        "\"twilio_sid\":\"%s\",\"sender_id\":\"%s\"," +
                        "\"is_verified\":%b,\"created_at\":\"%s\"}",
                        rs.getInt("id"),
                        esc(rs.getString("name")),
                        esc(rs.getString("email")),
                        esc(rs.getString("phone_number_msisdn")),
                        rs.getDate("birthday") != null ? rs.getDate("birthday").toString() : "",
                        esc(rs.getString("job")),
                        esc(rs.getString("address")),
                        esc(rs.getString("twilio_sid")),
                        esc(rs.getString("allowed_sender_id")),
                        rs.getBoolean("is_verified"),
                        rs.getTimestamp("created_at")
                    );
                } else {
                    out.print("{\"error\":\"Customer not found\"}");
                }
                return;
            }

            // --- List all customers ---
            PreparedStatement ps = conn.prepareStatement(
                "SELECT u.id, u.name, u.email, u.phone_number_msisdn, u.is_verified, u.created_at, " +
                "       COUNT(s.message_id) AS sms_count " +
                "FROM users u " +
                "LEFT JOIN sms_messages s ON u.id = s.user_id " +
                "WHERE u.role = 'CUSTOMER' " +
                "GROUP BY u.id, u.name, u.email, u.phone_number_msisdn, u.is_verified, u.created_at " +
                "ORDER BY u.created_at DESC"
            );
            ResultSet rs = ps.executeQuery();
            out.print("[");
            boolean first = true;
            while (rs.next()) {
                if (!first) out.print(",");
                first = false;
                out.printf(
                    "{\"id\":%d,\"name\":\"%s\",\"email\":\"%s\",\"phone\":\"%s\"," +
                    "\"is_verified\":%b,\"sms_count\":%d,\"created_at\":\"%s\"}",
                    rs.getInt("id"),
                    esc(rs.getString("name")),
                    esc(rs.getString("email")),
                    esc(rs.getString("phone_number_msisdn")),
                    rs.getBoolean("is_verified"),
                    rs.getInt("sms_count"),
                    rs.getTimestamp("created_at")
                );
            }
            out.print("]");

        } catch (Exception e) {
            e.printStackTrace();
            out.print("{\"error\":\"Server error\"}");
        } finally {
            try { if (conn != null) conn.close(); } catch (Exception ignored) {}
        }
    }

    // ── POST handler ───────────────────────────────────────────
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        if (!isAdmin(request, response)) return;

        String action  = request.getParameter("action");
        String idParam = request.getParameter("id");

        if (idParam == null || idParam.isBlank()) {
            response.setStatus(400);
            response.getWriter().println("Missing customer id");
            return;
        }

        int custId = Integer.parseInt(idParam);
        Connection conn = null;
        try {
            conn = DBConnection.getConnection();

            // --- Delete customer ---
            if ("delete".equals(action)) {
                PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM users WHERE id = ? AND role = 'CUSTOMER'");
                ps.setInt(1, custId);
                ps.executeUpdate();
                response.sendRedirect("admin_home.html?success=customer_deleted");
                return;
            }

            // --- Edit customer ---
            if ("edit".equals(action)) {
                String name    = request.getParameter("name");
                String email   = request.getParameter("email");
                String phone   = request.getParameter("phone");
                String job     = request.getParameter("job");
                String address = request.getParameter("address");

                PreparedStatement ps = conn.prepareStatement(
                    "UPDATE users SET name=?, email=?, phone_number_msisdn=?, job=?, address=? " +
                    "WHERE id=? AND role='CUSTOMER'"
                );
                ps.setString(1, name);
                ps.setString(2, email);
                ps.setString(3, phone);
                ps.setString(4, job);
                ps.setString(5, address);
                ps.setInt(6, custId);
                ps.executeUpdate();
                response.sendRedirect("admin_home.html?success=customer_updated");
                return;
            }

            response.setStatus(400);
            response.getWriter().println("Unknown action");

        } catch (Exception e) {
            e.printStackTrace();
            response.sendRedirect("admin_home.html?error=server_error");
        } finally {
            try { if (conn != null) conn.close(); } catch (Exception ignored) {}
        }
    }

    private String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
