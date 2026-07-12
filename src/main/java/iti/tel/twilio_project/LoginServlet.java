package iti.tel.twilio_project;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.sql.*;

/**
 * Authenticates users and starts an HttpSession.
 * Redirects ADMIN → admin_home.html, CUSTOMER → customer_home.html
 */
public class LoginServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String email    = request.getParameter("email");
        String password = request.getParameter("password");

        Connection conn = null;
        try {
            conn = DBConnection.getConnection();

            PreparedStatement ps = conn.prepareStatement(
                "SELECT id, name, role, is_verified, twilio_sid, twilio_token, allowed_sender_id, phone_number_msisdn " +
                "FROM users WHERE email = ? AND password = ?"
            );
            ps.setString(1, email);
            ps.setString(2, password);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                boolean isVerified = rs.getBoolean("is_verified");
                String role        = rs.getString("role");

                // Customers must verify their phone before logging in
                if ("CUSTOMER".equals(role) && !isVerified) {
                    response.sendRedirect("login.html?error=not_verified");
                    return;
                }

                // Build session
                HttpSession session = request.getSession(true);
                session.setAttribute("user_id",    rs.getInt("id"));
                session.setAttribute("user_name",  rs.getString("name"));
                session.setAttribute("user_email", email);
                session.setAttribute("user_role",  role);
                session.setAttribute("twilio_sid",    rs.getString("twilio_sid"));
                session.setAttribute("twilio_token",  rs.getString("twilio_token"));
                session.setAttribute("sender_id",     rs.getString("allowed_sender_id"));
                session.setAttribute("user_phone",    rs.getString("phone_number_msisdn"));
                session.setMaxInactiveInterval(30 * 60); // 30 minutes

                // Redirect based on role
                if ("ADMIN".equals(role)) {
                    response.sendRedirect("admin_home.html");
                } else {
                    response.sendRedirect("customer_home.html");
                }

            } else {
                response.sendRedirect("login.html?error=invalid_credentials");
            }

        } catch (Exception e) {
            e.printStackTrace();
            response.sendRedirect("login.html?error=server_error");
        } finally {
            try { if (conn != null) conn.close(); } catch (Exception ignored) {}
        }
    }
}
