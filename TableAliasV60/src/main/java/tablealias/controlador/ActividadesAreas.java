package tablealias.controlador;

import com.google.gson.Gson;
import java.io.IOException;
import java.util.List;
import java.util.zip.GZIPOutputStream;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import mx.inegi.dtweb.connection.DebugerLog;
import tablealias.actividadesareas.helpers.ActividadesAreasGrid;
import tablealias.actividadesareas.helpers.ActividadesAreasDto;
import tablealias.actividadesareas.helpers.ActividadesAreasGSon;
import tablealias.actividadesareas.helpers.SaveQuoteItem;
import tablealias.dinue.helpers.Actividades;
import tablealias.dinue.helpers.ActividadesAreasCache;
import tablealias.dinue.helpers.AreasGeo;
import tablealias.dinue.helpers.DenuePK;
import tablealias.dinue.helpers.Estratos;
import tablealias.sqlworkers.ActividadesAreasWrapper;
import tablealias.dinue.helpers.DinueOptionalParams;
import tablealias.sqlworkers.ParamConsultaWrapper;
import tablealias.utils.HTTPEncodingFormat;
import tablealias.utils.Parametreador;
import tablealias.utils.RegistraErrorWebService;
import tablealias.utils.RequestHelper;
import tablealias.utils.SubProjectReader;
import tablealias.utils.TablasServidor;

/**
 *
 * @author INEGI
 */
public class ActividadesAreas extends HttpServlet {

    /** 
     * Processes requests for both HTTP <code>GET</code> and <code>POST</code> methods.
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    protected void processRequest(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        ServletOutputStream out = null;
        response.addHeader("Access-Control-Allow-Origin", "*");
        response.setContentType(HTTPEncodingFormat.getJsonFormatWEncoding());
        String table2Search = null;
        try {
            ServletContext context = getServletContext();
            String areageo = request.getParameter("areageo");
            String actividades = request.getParameter("actividad");
            String resguardarItem = request.getParameter("saveitem");
            if (areageo == null || actividades == null || areageo.length() < 1 || actividades.length() < 1) {
                response.sendError( HttpServletResponse.SC_NOT_ACCEPTABLE, "Invalid Parameters");
                return;
            }
            AreasGeo ag = new AreasGeo(areageo);
            Actividades ac = new Actividades(actividades);
            TablasServidor tablasServidor = (TablasServidor) context.getAttribute("tablasServidor");
            String estratos = request.getParameter("estratos");
            Estratos ests = null;
            if (estratos != null && estratos.trim().length() > 0) {
                ests = new Estratos(estratos);
            }
            table2Search = request.getParameter("tabla");
            //hack 1 por aaron
            table2Search = "c501";
            //table2Search = "dinueoracle";
            if (tablasServidor.tableExists(table2Search)) {
                String subProject = SubProjectReader.getSubProjectString( tablasServidor.getFoundTable(), request );
                DinueOptionalParams optParams = new DinueOptionalParams(request, tablasServidor.getFoundServer());
                ActividadesAreasGrid grid = new ActividadesAreasGrid(ag, ac);
                ActividadesAreasWrapper aaw = new ActividadesAreasWrapper(grid, optParams, ests, subProject );
                aaw.setTable(tablasServidor.getFoundTable());
                aaw.setServer(tablasServidor.getFoundServer());
                RequestHelper rh = new RequestHelper(request);
                String res = null;
                ActividadesAreasCache cache = new ActividadesAreasCache();
                Parametreador par = new Parametreador();
                String llave = par.getDigestedParams(request, "iso-8859-1");
                res = null;//cache.getCache(llave, tablasServidor.getFoundServer());
                boolean cacheo = false;
                if (res == null || res.length() < 5) {
                    List<ActividadesAreasDto> dtos = aaw.doQuery();
                    ActividadesAreasGSon aag = new ActividadesAreasGSon(dtos, grid);
                    Gson gson = new Gson();
                    res = gson.toJson(aag);//formatter.getData();
                    cacheo = true;
                }
                String respaldo = new String(res);

                if (resguardarItem != null && resguardarItem.equalsIgnoreCase("simon")) {
                    SaveQuoteItem sv = new SaveQuoteItem();
                    ParamConsultaWrapper pcw = sv.SaveQuote(tablasServidor, actividades, areageo, estratos, request, ac, ag, ests);
                    boolean res1 = pcw.save();
                    if (res1) {
                        //System.out.println("Redirecionando a cotizacion");
                        DenuePK pk = pcw.getDenuePK();
                        String jsonAdditionalData = String.format("\"sessionItemID\":%d,%n \"totalUE\":%d,", pk.getId(),pcw.getTotales());
                        StringBuilder sb = new StringBuilder(res);
                        sb.insert(1, jsonAdditionalData);
                        res = sb.toString();
                    } else {
                        if (pcw.getTotales() > 0) {
                            response.sendError( HttpServletResponse.SC_BAD_REQUEST, pcw.getErrorMsj());
                            //out.write(pcw.getErrorMsj());
                        } else {
                            /*rd = request.getRequestDispatcher("ErrorTotales.jsp");
                            rd.forward(request, response);*/
                            response.sendError( HttpServletResponse.SC_BAD_REQUEST, "Error de totales");
                        }
                    }
                }
                if (rh.supportsGzip()) {
                    response.setHeader("Content-Encoding", "gzip");
                    out = response.getOutputStream();
                    GZIPOutputStream gzos = new GZIPOutputStream(out);
                    if (aaw.hasErrors()) {
                        gzos.write(aaw.getErrorMsg().getBytes());
                    } else {
                        if (cacheo) {
                            DebugerLog.log("voy a Cacheando con gzip");
                            cache.createCache(llave, respaldo, tablasServidor.getFoundServer());
                        }
                        gzos.write(res.getBytes());
                    }
                    gzos.close();

                } else {
                    if (cacheo) {
                        DebugerLog.log("voy a Cacheando sin gzip");
                        cache.createCache(llave, respaldo, tablasServidor.getFoundServer());
                    }
                    out = response.getOutputStream();
                    out.print(res);
                }
            }
        } catch (Exception ex) {
            RegistraErrorWebService.RegistraError(request, ex);
            ex.printStackTrace();
        } finally {
            out.close();
        }
    }

    // <editor-fold defaultstate="collapsed" desc="HttpServlet methods. Click on the + sign on the left to edit the code.">
    /** 
     * Handles the HTTP <code>GET</code> method.
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
    }

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.addHeader("Access-Control-Allow-Origin", "*");
        super.doOptions(req, resp);
    }

    /** 
     * Handles the HTTP <code>POST</code> method.
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
    }

    /** 
     * Returns a short description of the servlet.
     * @return a String containing servlet description
     */
    @Override
    public String getServletInfo() {
        return "Short description";
    }// </editor-fold>
}
