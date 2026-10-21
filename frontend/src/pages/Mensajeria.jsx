import { useCallback, useEffect, useState } from "react";
import Navbar from "../components/Navbar";
import { adminFetch, getUsuarioActual } from "../services/apiFetch";

const REFRESCO_MS = 5000;

const duracion = (segundos) => {
  if (segundos == null) return "—";
  if (segundos % 86400 === 0) return `${segundos / 86400} d`;
  if (segundos % 3600 === 0) return `${segundos / 3600} h`;
  return `${segundos} s`;
};

/**
 * Panel de mensajería (solo ADMIN): estado de las 3 colas y sus DLQ, consumidores por instancia,
 * pausar/reanudar, reprocesar y purgar DLQ. Consume la API de mensajeria-admin con el token de Cognito.
 */
export default function Mensajeria() {
  const [esAdmin, setEsAdmin] = useState(null);
  const [colas, setColas] = useState([]);
  const [consumidores, setConsumidores] = useState([]);
  const [error, setError] = useState("");
  const [aviso, setAviso] = useState("");
  const [ocupado, setOcupado] = useState("");

  const cargar = useCallback(async () => {
    try {
      const [listaColas, listaConsumidores] = await Promise.all([
        adminFetch("/api/admin/colas?prefijo=pedidos360"),
        adminFetch("/api/admin/consumidores"),
      ]);
      setColas(listaColas); setConsumidores(listaConsumidores); setError("");
    } catch (e) { setError(e.message); }
  }, []);

  useEffect(() => { getUsuarioActual().then((u) => setEsAdmin(Boolean(u?.grupos?.includes("ADMIN")))); }, []);
  useEffect(() => {
    if (!esAdmin) return undefined;
    cargar();
    const t = setInterval(cargar, REFRESCO_MS);
    return () => clearInterval(t);
  }, [esAdmin, cargar]);

  async function accion(clave, path, mensajeOk) {
    setOcupado(clave); setError(""); setAviso("");
    try {
      const r = await adminFetch(path, { method: "POST" });
      setAviso(typeof mensajeOk === "function" ? mensajeOk(r) : mensajeOk);
      await cargar();
    } catch (e) { setError(e.message); } finally { setOcupado(""); }
  }

  if (esAdmin === false) {
    return <div className="app-shell"><Navbar /><main className="page-wrap"><div className="notice notice-error">403: esta sección es solo para el grupo ADMIN.</div></main></div>;
  }

  const principales = colas.filter((c) => c.dlq);
  const porNombre = Object.fromEntries(colas.map((c) => [c.nombre, c]));
  const totalDlq = principales.reduce((s, c) => s + (porNombre[c.dlq]?.disponibles || 0), 0);

  return <div className="app-shell"><Navbar /><main className="page-wrap">
    <header className="page-heading"><div><span className="eyebrow">OPERACIONES · MENSAJERÍA SNS/SQS</span><h1>Mensajería</h1><p>Tópico pedidos360-eventos → 3 colas, cada una con su DLQ. Se actualiza cada 5 s.</p></div><span className="live-indicator">{totalDlq > 0 ? `● ${totalDlq} EN DLQ` : "● SIN FALLOS"}</span></header>
    {error && <div className="notice notice-error" role="alert">{error}</div>}
    {aviso && <div className="notice">{aviso}</div>}

    <section className="orders-section"><div className="section-heading"><div><span className="eyebrow">COLAS</span><h2>Estado de las colas</h2></div><span className="order-count">{principales.length} colas de trabajo</span></div>
      <div className="data-table-wrap"><table className="data-table"><thead><tr><th>Cola</th><th>Disponibles</th><th>En proceso</th><th>Retención</th><th>Visibility</th><th>DLQ</th><th>En DLQ</th><th>Acciones DLQ</th></tr></thead><tbody>
        {principales.map((c) => { const dlq = porNombre[c.dlq]; const enDlq = dlq?.disponibles || 0; return <tr key={c.nombre}>
          <td><strong>{c.nombre}</strong></td><td>{c.disponibles}</td><td>{c.enProceso}</td><td>{duracion(c.retencionSegundos)}</td><td>{duracion(c.visibilityTimeoutSegundos)}</td>
          <td>{c.dlq} <small>(máx. {c.maxReceiveCount})</small></td>
          <td><span className={`status-badge ${enDlq > 0 ? "status-cancelado" : "status-listo"}`}>{enDlq}</span></td>
          <td><button className="button button-secondary" disabled={!enDlq || ocupado !== ""} onClick={() => accion(`r-${c.nombre}`, `/api/admin/colas/${c.nombre}/dlq/reprocesar`, (r) => `${r.movidos} mensaje(s) devueltos de ${r.dlq} a ${r.colaDestino}`)}>Reprocesar</button>{" "}
            <button className="button button-quiet" disabled={!enDlq || ocupado !== ""} onClick={() => window.confirm(`¿Purgar ${c.dlq}? Se borran sus mensajes.`) && accion(`p-${c.nombre}`, `/api/admin/colas/${c.nombre}/dlq/purgar`, `${c.dlq} purgada`)}>Purgar</button></td>
        </tr>; })}
        {!principales.length && <tr><td colSpan="8">Sin datos. ¿Está corriendo mensajeria-admin y aplicada la topología?</td></tr>}
      </tbody></table></div>
    </section>

    <section className="orders-section"><div className="section-heading"><div><span className="eyebrow">CONSUMIDORES</span><h2>Listeners de eventos-service</h2></div><span className="order-count">{consumidores.length} en total</span></div>
      <div className="data-table-wrap"><table className="data-table"><thead><tr><th>Consumidor</th><th>Instancia</th><th>Estado</th><th>Acción</th></tr></thead><tbody>
        {consumidores.map((c, i) => <tr key={`${c.instancia}-${c.id}-${i}`}>
          <td><strong>{c.id || "—"}</strong></td><td>{c.instancia}</td>
          <td>{c.error ? <span className="status-badge status-cancelado">{c.error}</span> : <span className={`status-badge ${c.activo ? "status-listo" : "status-cancelado"}`}>{c.activo ? "ACTIVO" : "PAUSADO"}</span>}</td>
          <td>{c.id && !c.error && (c.activo
            ? <button className="button button-quiet" disabled={ocupado !== ""} onClick={() => accion(`c-${c.id}`, `/api/admin/consumidores/${c.id}/pausar`, `${c.id} pausado: los mensajes se acumulan en la cola`)}>Pausar</button>
            : <button className="button button-primary" disabled={ocupado !== ""} onClick={() => accion(`c-${c.id}`, `/api/admin/consumidores/${c.id}/reanudar`, `${c.id} reanudado`)}>Reanudar</button>)}</td>
        </tr>)}
      </tbody></table></div>
    </section>
  </main></div>;
}
