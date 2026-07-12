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
 * GET  /ProfileServlet → returns customer profile as JSON
 * POST /ProfileServlet → updates customer profile fields
 */
public class ProfileServlet extends HttpServlet {

    /** Return current user profile as JSON */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("user_id") == null) {
            response.setStatus(401);
            return;
        }

        int userId = (Integer) session.getAttribute("user_id");
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        Connection conn = null;
        try {
            conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(
                "SELECT name, email, phone_number_msisdn, birthday, job, address, " +
                "       twilio_sid, allowed_sender_id, role " +
                "FROM users WHERE id = ?"
            );
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                PrintWriter out = response.getWriter();
                out.printf(
                    "{\"name\":\"%s\",\"email\":\"%s\",\"phone\":\"%s\",\"birthday\":\"%s\"," +
                    "\"job\":\"%s\",\"address\":\"%s\",\"twilio_sid\":\"%s\",\"sender_id\":\"%s\",\"role\":\"%s\"}",
                    esc(rs.getString("name")),
                    esc(rs.getString("email")),
                    esc(rs.getString("phone_number_msisdn")),
                    rs.getDate("birthday") != null ? rs.getDate("birthday").toString() : "",
                    esc(rs.getString("job")),
                    esc(rs.getString("address")),
                    esc(rs.getString("twilio_sid")),
                    esc(rs.getString("allowed_sender_id")),
                    esc(rs.getString("role"))
                );
            }
        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(500);
        } finally {
            try { if (conn != null) conn.close(); } catch (Exception ignored) {}
        }
    }

    /** Update user profile */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("user_id") == null) {
            response.sendRedirect("login.html");
            return;
        }

        int userId = (Integer) session.getAttribute("user_id");

        String name     = request.getParameter("name");
        String birthday = request.getParameter("birthday");
        String phone    = request.getParameter("phone");
        String job      = request.getParameter("job");
        String address  = request.getParameter("address");
        String sid      = request.getParameter("twilio_sid");
        String token    = request.getParameter("twilio_token");
        String sender   = request.getParameter("sender_id");
        String password = request.getParameter("password");   // optional: only update if not blank

        Connection conn = null;
        try {
            conn = DBConnection.getConnection();

            // Build update query dynamically based on whether password is being changed
            String sql;
            if (password != null && !password.isBlank()) {
                sql = "UPDATE users SET name=?, phone_number_msisdn=?, birthday=?, job=?, address=?, " +
                      "twilio_sid=?, twilio_token=?, allowed_sender_id=?, password=? WHERE id=?";
            } else {
                sql = "UPDATE users SET name=?, phone_number_msisdn=?, birthday=?, job=?, address=?, " +
                      "twilio_sid=?, twilio_token=?, allowed_sender_id=? WHERE id=?";
            }

            PreparedStatement ps = conn.prepareStatement(sql);
            int idx = 1;
            ps.setString(idx++, name);
            ps.setString(idx++, phone);
            ps.setDate(idx++,   birthday != null && !birthday.isBlank() ? Date.valueOf(birthday) : null);
            ps.setString(idx++, job);
            ps.setString(idx++, address);
            ps.setString(idx++, sid);
            ps.setString(idx++, token);
            ps.setString(idx++, sender);
            if (password != null && !password.isBlank()) {
                ps.setString(idx++, password);
            }
            ps.setInt(idx, userId);
            ps.executeUpdate();

            // Update session name in case it changed
            session.setAttribute("user_name",  name);
            session.setAttribute("twilio_sid", sid);
            session.setAttribute("twilio_token", token);
            session.setAttribute("sender_id",  sender);
            session.setAttribute("user_phone", phone);

            response.sendRedirect("customer_home.html?success=profile_updated");

        } catch (Exception e) {
            e.printStackTrace();
            response.sendRedirect("customer_home.html?error=profile_update_failed");
        } finally {
            try { if (conn != null) conn.close(); } catch (Exception ignored) {}
        }
    }

    private String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
