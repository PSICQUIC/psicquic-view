package org.hupo.psi.mi.psicquic.clustering.job.batch;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hupo.psi.mi.psicquic.clustering.ClusteringContext;
import org.hupo.psi.mi.psicquic.clustering.job.ClusteringJob;
import org.hupo.psi.mi.psicquic.clustering.job.JobStatus;
import org.hupo.psi.mi.psicquic.clustering.job.dao.JobDao;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import psidev.psi.mi.search.SearchResult;
import psidev.psi.mi.search.Searcher;
import psidev.psi.mi.tab.model.BinaryInteraction;
import psidev.psi.mi.tab.model.builder.MitabDocumentDefinition;
import uk.ac.ebi.enfin.mi.cluster.ClusterContext;
import uk.ac.ebi.enfin.mi.cluster.Encore2Binary;
import uk.ac.ebi.enfin.mi.cluster.EncoreInteraction;
import uk.ac.ebi.enfin.mi.cluster.InteractionClusterAdv;
import uk.ac.ebi.enfin.mi.cluster.cache.CacheStrategy;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.*;

/**
 * A tasklet that wraps the Enfin clustering algorithm.
 *
 * @author Samuel Kerrien (skerrien@ebi.ac.uk)
 * @version $Id$
 * @since 0.1
 */
public class PsicquicClustererTasklet implements Tasklet { // , StepExecutionListener

    private static final Log log = LogFactory.getLog( PsicquicClustererTasklet.class );

    public static final String NEW_LINE = System.getProperty( "line.separator" );

//    private void builCache( String query, String pathToCache ) {
//
//        // TODO check that we can run multiple clustering simulatenously ... bottom line is we cannot share the cache !
//
//        final ClusterContext context = ClusterContext.getInstance();
//        context.setCacheStrategy( CacheStrategy.ON_DISK );
//
//        final CacheManager cacheManager = context.getCacheManager();
//        cacheManager.setResetCache( true );
//
//        final File cacheLocation = new File( pathToCache );
//        cacheManager.setCacheStorage( cacheLocation );
//        System.out.println( "Cache stored in: " + cacheLocation.getAbsolutePath() );
//
//        InteractionClusterAdv iC = new InteractionClusterAdv();
//
//        /* Query one or more IDs */
//        iC.addQueryAcc( query );
//
//        /* sources to query */
//        /* IMEX curated databases */
////        iC.addQuerySource("DIP");
//        iC.addQuerySource( "IntAct" );
//        iC.addQuerySource( "MINT" );
//        iC.addQuerySource( "MPact" );
//        iC.addQuerySource( "MatrixDB" );
//        iC.addQuerySource( "MPIDB" );
//        iC.addQuerySource( "BioGrid" );
//        iC.addQuerySource( "ChEMBL" );
//        iC.addQuerySource( "DIP" );
//        iC.addQuerySource( "InnateDB" );
//        iC.addQuerySource( "MPIDB" );
//        iC.addQuerySource( "Reactome" );
//
//        long start = System.currentTimeMillis();
//        iC.runService();
//        long stop = System.currentTimeMillis();
//        System.out.println( "Time to build: " + ( ( stop - start ) / 1000 ) + "s" );
//
//        System.out.println( "#Clusters: " + cacheManager.getInteractionCache().size() );
//
//        cacheManager.shutdown();
//    }
//
//    private void extractMitabFromCache( String pathToCache ) {
//        final ClusterContext context = ClusterContext.getInstance();
//        context.setCacheStrategy( CacheStrategy.ON_DISK );
//        final CacheManager cacheManager = context.getCacheManager();
//        cacheManager.setResetCache( false );
//
//        final File cacheLocation = new File( pathToCache );
//        ClusterContext.getInstance().getCacheManager().setCacheStorage( cacheLocation );
//        log.info( "Processing cache stored in: " + cacheLocation.getAbsolutePath() );
//
//        final Map<Integer, EncoreInteraction> cache = cacheManager.getInteractionCache();
//        log.info( "cluster count: " + cache.size() );
//
//        // clusters are stored iusing a cluster id ( 1 .. cache.size() )
//        final int clusterCount = cache.size();
//        for ( int i = 1; i <= clusterCount; i++ ) {
//
//            EncoreInteraction interaction = cache.get( i );
//
//            log.info( i + " of " + clusterCount );
//
//            // TODO get MITAB and store into Lucene
//
//        } // clusters
//
//        ClusterContext.getInstance().getCacheManager().shutdown();
//    }

    ////////////////
    // Tasklet

    public RepeatStatus execute( StepContribution contribution, ChunkContext chunkContext ) throws Exception {

        final Map<String, Object> params = chunkContext.getStepContext().getJobParameters();

        final String jobId = ( String ) params.get( "jobId" );
        if ( jobId == null ) {
            throw new IllegalArgumentException( "You must give a non null jobId" );
        }

        final JobDao jobDao = ClusteringContext.getInstance().getDaoFactory().getJobDao();
        final ClusteringJob job = jobDao.getJob( jobId );
        if( job == null ) {
            log.error( "The specified jobId cannot be found in storage: " + jobId );
            // TODO how can we notify the user if its job is lost ?
            return RepeatStatus.FINISHED;
        }

        log.info( "Processing clustering job: " + jobId );

        final String miql = ( String ) params.get( "miql" );
        if ( miql == null ) {
            throw new IllegalArgumentException( "You must give a non null miql" );
        }

        final String servicesStr = ( String ) params.get( "services" );
        if ( servicesStr == null ) {
            throw new IllegalArgumentException( "You must give a non null servicesStr" );
        }
        final List<String> services = new ArrayList<String>();
        if ( servicesStr.indexOf( "|" ) != -1 ) {
            services.addAll( Arrays.asList( servicesStr.split( "\\|" ) ) );
        } else {
            services.add( servicesStr );
        }

        log.debug( "PsicquicClustererTasklet.execute(MIQL='" + miql + "', " + services + ")" );

        final File dataLocationFile = ClusteringContext.getInstance().getConfig().getDataLocationFile();
        final File cacheStorageDirectory = new File( dataLocationFile, "clustering-cache" );
        boolean created = cacheStorageDirectory.mkdirs();
        if( ! created ) {
            // TODO handle error and abort process.
        }

        ClusterContext.getInstance().setCacheStrategy( CacheStrategy.IN_MEMORY );
//        ClusterContext.getInstance().setCacheStrategy( CacheStrategy.ON_DISK );
//        ClusterContext.getInstance().getCacheManager().setCacheStorage( cacheStorageDirectory );

        // Setup up enfin clustering
        InteractionClusterAdv iC = new InteractionClusterAdv();
        iC.addQueryAcc( miql );
        for ( String service : services ) {
            iC.addQuerySource( service );
        }

        // Run clustering
        iC.setMappingIdDbNames( "uniprotkb,irefindex,ddbj/embl/genbank,refseq,chebi" );
        iC.runService();

        // Extract MITAB lines from cluster cache
        Map<Integer, EncoreInteraction> interactionMapping = iC.getInteractionMapping();
        Encore2Binary iConverter = new Encore2Binary( iC.getMappingIdDbNames() );

        MitabDocumentDefinition documentDefinition = new MitabDocumentDefinition();

        log.debug( "-------- CLUSTERED MITAB ("+ interactionMapping.size() +") --------" );


        final File jobDirectory = new File( dataLocationFile, jobId );
        created = jobDirectory.mkdirs();
        if( ! created ) {
            // TODO handle error
        }

        log.debug( "Using Job directory: " + jobDirectory.getAbsolutePath());

        final String mitabFilename = jobId + ".tsv";
        final File mitabFile = new File( jobDirectory, mitabFilename );
        log.debug( "MITAB file: " + mitabFile.getAbsolutePath());
        BufferedWriter out = new BufferedWriter( new FileWriter( mitabFile ) );

        for ( Map.Entry<Integer, EncoreInteraction> entry : interactionMapping.entrySet() ) {
            final EncoreInteraction ei = entry.getValue();
            final BinaryInteraction bi = iConverter.getBinaryInteraction( ei );
            final String mitab = documentDefinition.interactionToString( bi );

            log.trace( mitab );
            out.write( mitab + NEW_LINE );
        }

        out.flush();
        out.close();

        // Build a Lucene index from MITAB
        final String luceneDirectoryName = "lucene-index";
        final File luceneDirectory = new File( jobDirectory, luceneDirectoryName );
        log.debug( "Lucene directory: " + luceneDirectory.getAbsolutePath());

        job.setLuceneIndexLocation( luceneDirectory.getAbsolutePath() );

        boolean hasHeader = false;

        try {
            Searcher.buildIndex(luceneDirectory.getAbsolutePath(), mitabFile.getAbsolutePath(), true, hasHeader);
            final SearchResult<BinaryInteraction> result = Searcher.search( "*", luceneDirectory.getAbsolutePath(), 0, 200 );
            log.info( "Indexed "+ result.getTotalCount() +" MITAB documents." );
        } catch ( Exception e ) {
            final String msg = "An error occured while performing the indexing of MITAB data clustered for job [miql='" +
                               miql + "', services='" + servicesStr + "', jobId='" + jobId + "']";
            log.error( msg, e );

            job.setStatus( JobStatus.FAILED );
            job.setStatusMessage( msg );
            job.setStatusException( e );
            job.setCompleted( new Date() );

            jobDao.update( job );

            return RepeatStatus.FINISHED;
        }

        // Update job repository
        job.setStatus( JobStatus.COMPLETED );

        jobDao.update( job );

        return RepeatStatus.FINISHED;
    }

//    /////////////////////////
//    // StepExecutionListener
//
//    public void beforeStep( StepExecution stepExecution ) {
//        System.out.println( "PsicquicClustererTasklet.beforeStep" );
//    }
//
//    public ExitStatus afterStep( StepExecution stepExecution ) {
//        System.out.println( "PsicquicClustererTasklet.afterStep" );
//        return ExitStatus.COMPLETED;
//    }
}