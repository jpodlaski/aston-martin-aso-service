import { BrowserRouter, Routes, Route } from "react-router-dom";

import Landing from "./pages/Landing";
import Login from "./pages/auth/Login";
import Register from "./pages/auth/Register";
import ForgotPassword from "./pages/auth/ForgotPassword";
import ResetPassword from "./pages/auth/ResetPassword";
import VerifyEmail from "./pages/auth/VerifyEmail";
import ResendVerification from "./pages/auth/ResendVerification";
import ConfirmAccountDeletion from "./pages/auth/ConfirmAccountDeletion";
import EmployeeLogin from "./pages/auth/EmployeeLogin";
import AddVehicle from "./pages/client/AddVehicle";
import ClientDashboard from "./pages/client/ClientDashboard";
import RequestService from "./pages/client/RequestService";
import EmployeeDashboard from "./pages/employee/EmployeeDashboard";
import AdminDashboard from "./pages/admin/AdminDashboard";
import PrivateRoute from "./components/PrivateRoute";
import SessionSync from "./components/SessionSync";
import FakeCookieBanner from "./components/FakeCookieBanner";
import { MANAGEMENT_ROLES, WORKSHOP_ROLES } from "./constants/roles";

/**
 * Front-door routing for the three user worlds:
 * - public landing / auth
 * - CLIENT → /client*
 * - workshop staff → /employee
 * - management → /admin
 */
export default function App() {
  return (
      <BrowserRouter>
        <SessionSync />
        <FakeCookieBanner />
        <Routes>
          <Route path="/" element={<Landing />} />
          <Route path="/login" element={<Login />} />
          <Route path="/register" element={<Register />} />
          <Route path="/forgot-password" element={<ForgotPassword />} />
          <Route path="/reset-password" element={<ResetPassword />} />
          <Route path="/verify-email" element={<VerifyEmail />} />
          <Route path="/resend-verification" element={<ResendVerification />} />
          <Route path="/confirm-account-deletion" element={<ConfirmAccountDeletion />} />
          <Route path="/employee-login" element={<EmployeeLogin />} />
          <Route
              path="/client"
              element={
                <PrivateRoute roles={["CLIENT"]}>
                  <ClientDashboard />
                </PrivateRoute>
              }
          />
          <Route
              path="/client/add-vehicle"
              element={
                <PrivateRoute roles={["CLIENT"]}>
                  <AddVehicle />
                </PrivateRoute>
              }
          />
          <Route
              path="/client/request-service"
              element={
                <PrivateRoute roles={["CLIENT"]}>
                  <RequestService />
                </PrivateRoute>
              }
          />
          <Route
              path="/employee"
              element={
                <PrivateRoute roles={WORKSHOP_ROLES}>
                  <EmployeeDashboard />
                </PrivateRoute>
              }
          />
          <Route
              path="/admin"
              element={
                <PrivateRoute roles={MANAGEMENT_ROLES}>
                  <AdminDashboard />
                </PrivateRoute>
              }
          />
        </Routes>
      </BrowserRouter>
  );
}
