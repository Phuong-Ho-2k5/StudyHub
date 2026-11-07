package com.studyhub.concept.recommendation;

import java.lang.reflect.*;
import java.sql.*;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.AbstractDataSource;

/** Counts JDBC statements, including queries invisible to Hibernate statistics. */
final class CountingDataSource extends AbstractDataSource {
    private final DataSource delegate;
    private int statements;
    CountingDataSource(DataSource delegate) {this.delegate=delegate;}
    int statements() {return statements;}
    void reset() {statements=0;}
    @Override public Connection getConnection() throws SQLException {return counted(delegate.getConnection());}
    @Override public Connection getConnection(String username,String password) throws SQLException {
        return counted(delegate.getConnection(username,password));
    }
    private Connection counted(Connection connection) {
        return (Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class<?>[]{Connection.class},(proxy,method,args)->{
            if(method.getName().equals("prepareStatement") || method.getName().equals("createStatement")) statements++;
            try {return method.invoke(connection,args);} catch(InvocationTargetException ex) {throw ex.getCause();}
        });
    }
}
