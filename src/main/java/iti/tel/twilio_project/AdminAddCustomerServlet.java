package iti.tel.twilio_project;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.sql.*;

/**
 * Admin-only: manually add a new Customer account.
 * Admin-created accounts are marked as verified immediately.
 * POST /AdminAddCustomerServlet
 */
public class AdminAddCustomerServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        // Admin guard
        HttpSession session = request.getSession(false);
        if (session == null || !"ADMIN".equals(session.getAttribute("user_role"))) {
            response.setStatus(403);
            return;
        }

        String name     = request.getParameter("name");
        String email    = request.getParameter("email");
        String password = request.getParameter("password");
        String phone    = request.getParameter("phone");
        String birthday = request.getParameter("birthday");
        String job      = request.getParameter("job");
        String address  = request.getParameter("address");
        String sid      = request.getParameter("twilio_sid");
        String token    = request.getParameter("twilio_token");
        String sender   = request.getParameter("sender_id");

        Connection conn = null;
        try {
            conn = DBConnection.getConnection();

            // Check email uniqueness
            PreparedStatement check = conn.prepareStatement(
                "SELECT id FROM users WHERE email = ?");
            check.setString(1, email);
            if (check.executeQuery().next()) {
                response.sendRedirect("admin_home.html?error=email_exists");
                return;
            }

            // Insert — admin-created accounts are auto-verified
            PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO users " +
                "(name, email, password, phone_number_msisdn, birthday, job, address, " +
                " twilio_sid, twilio_token, allowed_sender_id, role, is_verified) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'CUSTOMER', true)"
            );
            ps.setString(1, name);
            ps.setString(2, email);
            ps.setString(3, password);
            ps.setString(4, phone);
            ps.setDate(5,   birthday != null && !birthday.isBlank() ? Date.valueOf(birthday) : null);
            ps.setString(6, job);
            ps.setString(7, address);
            ps.setString(8, sid);
            ps.setString(9, token);
            ps.setString(10, sender);
            ps.executeUpdate();

            response.sendRedirect("admin_home.html?success=customer_added");

        } catch (Exception e) {
            e.printStackTrace();
            response.sendRedirect("admin_home.html?error=server_error");
        } finally {
            try { if (conn != null) conn.close(); } catch (Exception ignored) {}
        }
    }
}
