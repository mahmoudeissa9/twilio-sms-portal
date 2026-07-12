package iti.tel.twilio_project;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.*;

/**
 * Verifies the OTP entered by the customer after registration.
 * Marks the user as verified and the OTP as used.
 */
public class VerifyServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String otp = request.getParameter("full_otp");

        Connection conn = null;
        try {
            conn = DBConnection.getConnection();

            // Find a valid, unused, non-expired OTP
            PreparedStatement ps = conn.prepareStatement(
                "SELECT user_id FROM verification " +
                "WHERE code = ? AND is_used = false AND expires_at > NOW() " +
                "ORDER BY id DESC LIMIT 1"
            );
            ps.setString(1, otp);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                int userId = rs.getInt("user_id");

                // Mark user as verified
                PreparedStatement ps2 = conn.prepareStatement(
                    "UPDATE users SET is_verified = true WHERE id = ?");
                ps2.setInt(1, userId);
                ps2.executeUpdate();

                // Mark OTP as used
                PreparedStatement ps3 = conn.prepareStatement(
                    "UPDATE verification SET is_used = true WHERE code = ? AND user_id = ?");
                ps3.setString(1, otp);
                ps3.setInt(2, userId);
                ps3.executeUpdate();

                // Clear pending session data
                request.getSession().removeAttribute("pending_email");

                // Redirect to login page with success message
                response.sendRedirect("login.html?verified=true");

            } else {
                // Invalid or expired OTP – redirect back with error
                response.sendRedirect("verify.html?error=invalid_otp");
            }

        } catch (Exception e) {
            e.printStackTrace();
            response.sendRedirect("verify.html?error=server_error");
        } finally {
            try { if (conn != null) conn.close(); } catch (Exception ignored) {}
        }
    }
}
