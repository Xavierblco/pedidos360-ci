import { useEffect, useState } from "react";
import { Link, useLocation } from "react-router-dom";
import { useAuthenticator } from "@aws-amplify/ui-react";
import { getUsuarioActual } from "../services/apiFetch";

export default function Navbar() {
  const { signOut, user } = useAuthenticator((context) => [context.user]);
  const { pathname } = useLocation();
  const [esAdmin, setEsAdmin] = useState(false);
  useEffect(() => { getUsuarioActual().then((u) => setEsAdmin(Boolean(u?.grupos?.includes("ADMIN")))).catch(() => setEsAdmin(false)); }, []);
  const links = [["/dashboard", "Inicio"], ["/productos", "Productos"], ["/pedidos", "Pedidos"], ...(esAdmin ? [["/mensajeria", "Mensajería"]] : [])];
  return <nav className="topbar"><Link className="brand" to="/dashboard"><span className="brand-mark">P</span><span>Pedidos<span style={{ color: "#e9b85e" }}>360</span></span></Link><div className="nav-links">{links.map(([href, label]) => <Link key={href} className={`nav-link ${pathname === href ? "active" : ""}`} to={href}>{label}</Link>)}</div><div className="nav-account"><span className="account-email">{user?.signInDetails?.loginId}</span><button className="button button-quiet" onClick={signOut}>Salir</button></div></nav>;
}
